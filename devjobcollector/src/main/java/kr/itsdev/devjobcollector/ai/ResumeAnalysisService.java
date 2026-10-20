package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeAnalysisService {
    public static final String CONSENT_VERSION = "local-analysis-v1";
    private final JdbcTemplate jdbc;
    private final CurrentMemberService members;
    private final LocalRecommendationEngine engine;
    private final ObjectMapper mapper;

    public ResumeAnalysisService(JdbcTemplate jdbc, CurrentMemberService members,
                                 LocalRecommendationEngine engine, ObjectMapper mapper) {
        this.jdbc = jdbc; this.members = members; this.engine = engine; this.mapper = mapper;
    }

    @Transactional
    public ObjectNode create(String subject, long resumeId, String key, String consentVersion,
                             LocalRecommendationEngine.Preferences preferences) {
        if (!CONSENT_VERSION.equals(consentVersion)) throw failure(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,64}")) throw failure(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
        long user = members.requireCurrentMember(subject).getId();
        // Serialize quota, cache and idempotency checks per member across all application instances.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
        String requestHash = hash(json(List.of(resumeId, preferences, consentVersion)));
        var bindings = jdbc.queryForList(
                "SELECT analysis_id, request_hash FROM ai_analysis_idempotency WHERE user_id=? AND idempotency_key=? FOR UPDATE",
                user, key);
        if (!bindings.isEmpty()) {
            var binding = bindings.getFirst();
            if (!requestHash.equals(binding.get("request_hash"))) throw failure(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
            return getOwned(user, ((Number) binding.get("analysis_id")).longValue());
        }
        var resumes = jdbc.queryForList("SELECT content_json FROM resumes WHERE id=? AND user_id=? FOR UPDATE", String.class, resumeId, user);
        if (resumes.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "RESUME_NOT_FOUND");
        JsonNode content = read(resumes.getFirst());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // Prevent unbounded idempotency aliases even when all requests hit the result cache.
        int bindingsToday = jdbc.queryForList(
                "SELECT idempotency_key FROM ai_analysis_idempotency WHERE user_id=? AND created_at>=? FOR UPDATE",
                String.class, user, today.atStartOfDay()).size();
        if (bindingsToday >= 100) throw failure(HttpStatus.TOO_MANY_REQUESTS, "REQUEST_LIMIT");
        var skills = engine.skills(content);
        var candidates = candidates(skills, preferences, today);
        String cacheKey = hash(json(List.of(resumeId, hash(resumes.getFirst()), preferences, candidates,
                engine.algorithmVersion(), today.toString(), CONSENT_VERSION)));
        var cached = jdbc.queryForList("SELECT id FROM ai_analysis_request WHERE user_id=? AND cache_key=? FOR UPDATE",
                Long.class, user, cacheKey);
        if (!cached.isEmpty()) {
            bind(user, key, requestHash, cached.getFirst());
            return getOwned(user, cached.getFirst());
        }
        jdbc.update("INSERT IGNORE INTO ai_analysis_daily_usage(user_id,usage_date,request_count) VALUES(?,?,0)", user, today);
        int changed = jdbc.update("""
                UPDATE ai_analysis_daily_usage SET request_count=request_count+1
                WHERE user_id=? AND usage_date=? AND request_count<5
                """, user, today);
        if (changed != 1) throw failure(HttpStatus.TOO_MANY_REQUESTS, "DAILY_ANALYSIS_LIMIT");
        ObjectNode result = mapper.createObjectNode();
        result.put("mode", "LOCAL");
        result.put("piiGatePassed", false);
        result.put("rawTextExternalCallCount", 0);
        result.put("rawTextExternalProcessingStatus", "BLOCKED_PII");
        result.put("algorithmVersion", engine.algorithmVersion());
        result.put("freshnessBasis", "CREATED_AT");
        result.put("candidateCount", candidates.size());
        result.put("asOf", today.toString());
        result.set("recommendations", mapper.valueToTree(engine.rank(skills, preferences, candidates, today)));
        result.set("improvements", mapper.valueToTree(engine.review(content)));
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO ai_analysis_request(user_id,resume_id,cache_key,recommendation_algo_version,consent_version,status,result_json)
                    VALUES(?,?,?,?,?,'COMPLETED_LOCAL',?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, user); statement.setLong(2, resumeId); statement.setString(3, cacheKey);
            statement.setString(4, engine.algorithmVersion()); statement.setString(5, consentVersion);
            statement.setString(6, json(result));
            return statement;
        }, keys);
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        jdbc.update("INSERT INTO ai_analysis_audit(analysis_id,gate_version) VALUES(?,?)", id, ResumePiiGate.VERSION);
        bind(user, key, requestHash, id);
        return getOwned(user, id);
    }

    @Transactional(readOnly = true)
    public ObjectNode get(String subject, long id) {
        return getOwned(members.requireCurrentMember(subject).getId(), id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String subject) {
        long user = members.requireCurrentMember(subject).getId();
        return jdbc.query("""
                SELECT a.id,a.resume_id,a.status,a.created_at,r.title
                FROM ai_analysis_request a JOIN resumes r ON r.id=a.resume_id
                WHERE a.user_id=? ORDER BY a.id DESC LIMIT 30
                """, (row, index) -> Map.<String, Object>of("id", row.getLong("id"), "resumeId", row.getLong("resume_id"),
                "status", row.getString("status"), "createdAt", row.getTimestamp("created_at").toLocalDateTime().toString(),
                "resumeTitle", row.getString("title")), user);
    }

    @Transactional
    public void delete(String subject, long id) {
        long user = members.requireCurrentMember(subject).getId();
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
        if (jdbc.update("DELETE FROM ai_analysis_request WHERE id=? AND user_id=?", id, user) != 1) {
            throw failure(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND");
        }
        // Daily usage intentionally survives deletion: deleting results must not reset the quota.
    }

    @Transactional
    public void feedback(String subject, long id, long jobId, String type) {
        if (!Set.of("MATCHED", "CAREER_MISMATCH", "SKILL_MISMATCH", "LOCATION_MISMATCH",
                "ALREADY_SEEN", "NOT_INTERESTED", "INACCURATE_JOB").contains(type)) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK");
        }
        long user = members.requireCurrentMember(subject).getId();
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
        ObjectNode result = getOwned(user, id);
        boolean found = false;
        for (JsonNode recommendation : result.path("recommendations")) {
            if (recommendation.path("jobId").asLong() == jobId) found = true;
        }
        if (!found) throw failure(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND");
        jdbc.update("""
                INSERT INTO ai_recommendation_feedback(analysis_id,job_post_id,feedback_type) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE feedback_type=VALUES(feedback_type)
                """, id, jobId, type);
    }

    private ObjectNode getOwned(long user, long id) {
        var rows = jdbc.queryForList("SELECT resume_id,status,result_json,created_at FROM ai_analysis_request WHERE id=? AND user_id=? FOR SHARE", id, user);
        if (rows.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND");
        var row = rows.getFirst();
        ObjectNode result = (ObjectNode) read(row.get("result_json").toString());
        result.put("id", id);
        result.put("resumeId", ((Number) row.get("resume_id")).longValue());
        result.put("status", row.get("status").toString());
        result.put("createdAt", row.get("created_at").toString());
        var visible = mapper.createArrayNode();
        // Recheck live visibility on every read, including cached and idempotent responses.
        for (JsonNode recommendation : result.path("recommendations")) {
            long jobId = recommendation.path("jobId").asLong();
            var jobs = jdbc.queryForList("""
                    SELECT title,company_name,location,experience FROM job_posts
                    WHERE id=? AND is_active=1 AND moderation_status='ACTIVE' AND end_date>=?
                    """, jobId, LocalDate.now(ZoneId.of("Asia/Seoul")));
            if (jobs.isEmpty()) continue;
            ObjectNode item = (ObjectNode) recommendation.deepCopy();
            var job = jobs.getFirst();
            item.put("title", Objects.toString(job.get("title"), ""));
            item.put("companyName", Objects.toString(job.get("company_name"), ""));
            item.put("location", Objects.toString(job.get("location"), ""));
            item.put("experience", Objects.toString(job.get("experience"), ""));
            var feedback = jdbc.queryForList("SELECT feedback_type FROM ai_recommendation_feedback WHERE analysis_id=? AND job_post_id=?",
                    String.class, id, jobId);
            if (!feedback.isEmpty()) item.put("feedback", feedback.getFirst());
            visible.add(item);
        }
        result.set("recommendations", visible);
        return result;
    }

    private void bind(long user, String key, String hash, long id) {
        jdbc.update("INSERT INTO ai_analysis_idempotency(user_id,idempotency_key,request_hash,analysis_id) VALUES(?,?,?,?)", user, key, hash, id);
    }

    private List<LocalRecommendationEngine.Candidate> candidates(Set<String> skills,
            LocalRecommendationEngine.Preferences preferences, LocalDate today) {
        var tagIds = jdbc.query("SELECT id,stack_name FROM tech_stacks",
                (row, index) -> Map.entry(row.getInt("id"), LocalRecommendationEngine.normalizeSkill(row.getString("stack_name"))))
                .stream().filter(entry -> skills.contains(entry.getValue())).map(Map.Entry::getKey).toList();
        String category = preferences.category() == null ? "" : preferences.category();
        if (tagIds.isEmpty() && category.isBlank()) return List.of();
        String placeholders = tagIds.isEmpty() ? "NULL" : String.join(",", Collections.nCopies(tagIds.size(), "?"));
        String match = "(SELECT COUNT(*) FROM post_tags p WHERE p.job_post_id=j.id AND p.tech_stack_id IN (" + placeholders + "))";
        String sql = "SELECT j.id,j.job_category,j.experience,j.location,j.hire_type,DATE(j.created_at) created_date,"
                + match + " skill_hits FROM job_posts j WHERE j.is_active=1 AND j.moderation_status='ACTIVE' AND j.end_date>=?"
                + " HAVING skill_hits>0 OR (?<>'' AND job_category LIKE ?)"
                + " ORDER BY skill_hits DESC,j.created_at DESC,j.id ASC LIMIT 100";
        var args = new ArrayList<Object>(tagIds);
        args.add(today); args.add(category); args.add("%" + category.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        var candidates = jdbc.query(sql, (row, index) -> new LocalRecommendationEngine.Candidate(row.getLong("id"),
                row.getString("job_category"), row.getString("experience"), row.getString("location"), row.getString("hire_type"),
                row.getDate("created_date") == null ? null : row.getDate("created_date").toLocalDate(), new ArrayList<>()), args.toArray());
        if (candidates.isEmpty()) return List.of();
        var ids = candidates.stream().map(LocalRecommendationEngine.Candidate::id).toList();
        var tags = jdbc.query("SELECT p.job_post_id,t.stack_name FROM post_tags p JOIN tech_stacks t ON t.id=p.tech_stack_id WHERE p.job_post_id IN ("
                + String.join(",", Collections.nCopies(ids.size(), "?")) + ") ORDER BY p.job_post_id,t.stack_name",
                (row, index) -> Map.entry(row.getLong("job_post_id"), row.getString("stack_name")), ids.toArray());
        var byId = new HashMap<Long, LocalRecommendationEngine.Candidate>();
        candidates.forEach(candidate -> byId.put(candidate.id(), candidate));
        tags.forEach(tag -> byId.get(tag.getKey()).skills().add(tag.getValue()));
        return candidates;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Analysis serialization failed"); }
    }
    private JsonNode read(String value) {
        try { return mapper.readTree(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Analysis data invalid"); }
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static org.springframework.web.server.ResponseStatusException failure(HttpStatus status, String code) {
        return ResumeTextExtractor.failure(status, code);
    }
}
