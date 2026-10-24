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
    public static final String CONSENT_VERSION = "local-analysis-v3";
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
        return createInternal(subject, resumeId, key, consentVersion, preferences, null);
    }

    @Transactional
    public ObjectNode createSaved(String subject, long resumeId, String key, String consentVersion,
                                  SavedResumeAnalysisInput.Prepared prepared) {
        return createInternal(subject, resumeId, key, consentVersion, prepared.preferences(), prepared);
    }

    @Transactional(readOnly = true)
    public ObjectNode savedRetry(String subject, long resumeId, String key, String consentVersion) {
        long user = members.requireCurrentMember(subject).getId();
        var rows = jdbc.queryForList("SELECT analysis_id,request_hash FROM ai_analysis_idempotency WHERE user_id=? AND idempotency_key=?", user, key);
        if (rows.isEmpty()) return null;
        String expected = hash(json(List.of(resumeId, SavedResumeAnalysisInput.VERSION, consentVersion)));
        if (!expected.equals(rows.getFirst().get("request_hash"))) throw failure(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
        return getOwned(user, ((Number) rows.getFirst().get("analysis_id")).longValue());
    }

    private ObjectNode createInternal(String subject, long resumeId, String key, String consentVersion,
                                     LocalRecommendationEngine.Preferences preferences, SavedResumeAnalysisInput.Prepared prepared) {
        if (!CONSENT_VERSION.equals(consentVersion)) throw failure(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,64}")) throw failure(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
        long user = members.requireCurrentMember(subject).getId();
        // Serialize quota, cache and idempotency checks per member across all application instances.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
        String requestHash = hash(json(List.of(resumeId, prepared == null ? preferences : SavedResumeAnalysisInput.VERSION, consentVersion)));
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
        if (prepared != null) {
            var ids = jdbc.queryForList("SELECT id FROM resume_attachments WHERE resume_id=? ORDER BY id FOR SHARE", Long.class, resumeId);
            if (!prepared.contentHash().equals(hash(resumes.getFirst())) || !prepared.attachmentIds().equals(ids)) {
                throw failure(HttpStatus.CONFLICT, "RESUME_CHANGED");
            }
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // Prevent unbounded idempotency aliases even when all requests hit the result cache.
        int bindingsToday = jdbc.queryForList(
                "SELECT idempotency_key FROM ai_analysis_idempotency WHERE user_id=? AND created_at>=? FOR UPDATE",
                String.class, user, today.atStartOfDay()).size();
        if (bindingsToday >= 100) throw failure(HttpStatus.TOO_MANY_REQUESTS, "REQUEST_LIMIT");
        var skills = prepared == null ? engine.skills(content) : prepared.skills();
        var candidates = candidates(skills, preferences, today);
        String cacheKey = hash(json(List.of(resumeId, hash(resumes.getFirst()), preferences, candidates,
                engine.algorithmVersion(), ResumeEvidenceReview.VERSION, ResumeContentReviewRules.VERSION, SavedResumeAnalysisInput.VERSION, today.toString(), CONSENT_VERSION,
                prepared == null ? "MANUAL" : prepared)));
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
        result.set("detectedSkills", mapper.valueToTree(skills));
        result.set("derivedPreferences", mapper.valueToTree(preferences));
        if (prepared != null) {
            result.put("inputMode", "SAVED_RESUME");
            result.put("inputFingerprint", SavedResumeAnalysisInput.fingerprint(prepared));
            result.set("sources", mapper.valueToTree(prepared.attachments()));
        }
        result.set("review", mapper.valueToTree(ResumeEvidenceReview.review(
                prepared == null ? ResumeEvidenceReview.savedDocuments(content) : prepared.documents(), skills)));
        var previousReports = jdbc.queryForList("SELECT id,result_json FROM ai_analysis_request WHERE user_id=? AND resume_id=? ORDER BY id DESC LIMIT 1", user, resumeId);
        if (!previousReports.isEmpty()) {
            var previous = previousReports.getFirst();
            JsonNode oldReview = read(previous.get("result_json").toString()).path("review");
            if (ResumeEvidenceReview.VERSION.equals(oldReview.path("version").asText())) {
                var oldKeys = new HashSet<String>();
                oldReview.path("findings").forEach(f -> oldKeys.add(f.path("dedupKey").asText()));
                var newKeys = new HashSet<String>();
                result.path("review").path("findings").forEach(f -> newKeys.add(f.path("dedupKey").asText()));
                long retained = newKeys.stream().filter(oldKeys::contains).count();
                result.set("comparison", mapper.valueToTree(Map.of("previousAnalysisId", previous.get("id"),
                        "retained", retained, "newFindings", newKeys.size() - retained, "notDetected", oldKeys.size() - retained)));
            }
        }
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

    /** Live job discovery is separate from the immutable analysis/AI evidence snapshot. */
    @Transactional(readOnly = true)
    public ObjectNode currentRecommendations(String subject, long id) {
        return currentRecommendationsOwned(members.requireCurrentMember(subject).getId(), id);
    }

    /** Read-only home discovery: never creates analyses or consumes external AI quota. */
    @Transactional(readOnly = true)
    public ObjectNode homeRecommendations(String subject) {
        long user = members.requireCurrentMember(subject).getId();
        var resumes = jdbc.queryForList("SELECT id,title,content_json,updated_at FROM resumes WHERE user_id=? ORDER BY updated_at DESC,id DESC LIMIT 1", user);
        if (resumes.isEmpty()) {
            var empty = mapper.createObjectNode();
            empty.putArray("recommendations");
            empty.putObject("recommendationStatus").put("reason", "NO_RESUME");
            return empty;
        }
        var resume = resumes.getFirst();
        long resumeId = ((Number) resume.get("id")).longValue();
        // Reuse AI evidence only when the resume has not changed since that analysis.
        var analyses = jdbc.queryForList("SELECT id FROM ai_analysis_request WHERE user_id=? AND resume_id=? AND status='COMPLETED_LOCAL' AND created_at>=? ORDER BY id DESC LIMIT 1",
                Long.class, user, resumeId, resume.get("updated_at"));
        if (!analyses.isEmpty()) return currentRecommendationsOwned(user, analyses.getFirst());
        JsonNode content = read(resume.get("content_json").toString());
        var skills = new TreeSet<>(engine.skills(content));
        var documents = ResumeEvidenceReview.savedDocuments(content);
        var vocabulary = new ArrayList<>(jdbc.queryForList("SELECT stack_name FROM tech_stacks", String.class));
        vocabulary.addAll(ResumeSkillVocabulary.NAMES);
        for (var document : documents) skills.addAll(SavedResumeAnalysisInput.findSkills(document.text(), vocabulary));
        var profile = content.path("careerProfile");
        var categories = SavedResumeAnalysisInput.allowed(profile.path("category"), "백엔드|프론트엔드|풀스택|데이터|인공지능|모바일|보안|DevOps");
        var preferences = new LocalRecommendationEngine.Preferences(SavedResumeAnalysisInput.careerYears(content), categories,
                SavedResumeAnalysisInput.allowed(profile.path("location"), "서울|경기|인천|부산|대구|대전|광주|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주|원격"),
                SavedResumeAnalysisInput.allowed(profile.path("employmentType"), "정규직|계약직|인턴|프리랜서"));
        var result = mapper.createObjectNode();
        result.put("resumeId", resumeId);
        result.put("inputMode", "HOME_RESUME");
        result.set("detectedSkills", mapper.valueToTree(skills));
        result.set("derivedPreferences", mapper.valueToTree(preferences));
        result.putArray("recommendations");
        return currentRecommendationsForInput(user, null, result);
    }

    private ObjectNode currentRecommendationsOwned(long user, long id) {
        ObjectNode result = getOwned(user, id); // Verify ownership before any resume or job lookup.
        return currentRecommendationsForInput(user, id, result);
    }

    private ObjectNode currentRecommendationsForInput(long user, Long id, ObjectNode result) {
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var skills = new TreeSet<String>();
        result.path("detectedSkills").forEach(s -> skills.add(LocalRecommendationEngine.normalizeSkill(s.asText())));
        var preferences = result.has("derivedPreferences")
                ? mapper.convertValue(result.get("derivedPreferences"), LocalRecommendationEngine.Preferences.class)
                : new LocalRecommendationEngine.Preferences(null, List.of(), List.of(), List.of());
        String roleSource = "ANALYSIS";
        if (preferences.category().isEmpty()) {
            String title = jdbc.queryForObject("SELECT title FROM resumes WHERE id=? AND user_id=?", String.class,
                    result.path("resumeId").asLong(), user);
            var inferred = RecommendationRoles.detect(title);
            if (!inferred.isEmpty()) {
                preferences = new LocalRecommendationEngine.Preferences(preferences.careerYears(), inferred,
                        preferences.location(), preferences.employmentType());
                roleSource = "RESUME_TITLE";
            }
        }
        var pool = candidates(skills, preferences, today);
        var ranked = engine.rank(skills, preferences, pool, today, pool.size());
        var feedback = new HashMap<Long, String>();
        // The latest feedback for this member/resume wins, including feedback on older analyses.
        var feedbackRows = jdbc.queryForList("""
                SELECT f.job_post_id,f.feedback_type FROM ai_recommendation_feedback f
                JOIN ai_analysis_request a ON a.id=f.analysis_id
                WHERE a.user_id=? AND a.resume_id=? ORDER BY f.updated_at,a.id
                """, user, result.path("resumeId").asLong());
        feedbackRows
                .forEach(row -> feedback.put(((Number) row.get("job_post_id")).longValue(), row.get("feedback_type").toString()));
        var originalIds = new HashSet<Long>();
        result.path("recommendations").forEach(j -> originalIds.add(j.path("jobId").asLong()));
        var visible = mapper.createArrayNode();
        var excluded = mapper.createArrayNode();
        // Read metadata in one query, independent of candidate count.
        var metadata = new HashMap<Long, Map<String, Object>>();
        var stacks = new HashMap<Long, List<Map<String, Object>>>();
        if (!ranked.isEmpty()) {
            var ids = ranked.stream().map(LocalRecommendationEngine.Recommendation::jobId).toList();
            var args = new ArrayList<Object>(ids);
            args.add(today);
            jdbc.queryForList("SELECT id,title,company_name,location,experience,end_date,hire_type,source_platform FROM job_posts WHERE id IN ("
                    + String.join(",", Collections.nCopies(ids.size(), "?"))
                    + ") AND is_active=1 AND moderation_status='ACTIVE' AND (end_date IS NULL OR end_date>=?)", args.toArray())
                    .forEach(row -> metadata.put(((Number) row.get("id")).longValue(), row));
            jdbc.queryForList("SELECT p.job_post_id,t.id,t.stack_name FROM post_tags p JOIN tech_stacks t ON t.id=p.tech_stack_id WHERE p.job_post_id IN ("
                    + String.join(",", Collections.nCopies(ids.size(), "?")) + ") ORDER BY t.stack_name", ids.toArray())
                    .forEach(row -> stacks.computeIfAbsent(((Number) row.get("job_post_id")).longValue(), key -> new ArrayList<>())
                            .add(Map.of("id", row.get("id"), "stackName", row.get("stack_name"))));
        }
        for (var recommendation : ranked) {
            var job = metadata.get(recommendation.jobId());
            if (job == null) continue;
            ObjectNode item = mapper.valueToTree(recommendation);
            item.put("title", Objects.toString(job.get("title"), ""));
            item.put("companyName", Objects.toString(job.get("company_name"), ""));
            item.put("location", Objects.toString(job.get("location"), ""));
            item.put("experience", Objects.toString(job.get("experience"), ""));
            item.put("endDate", Objects.toString(job.get("end_date"), ""));
            item.put("hireType", Objects.toString(job.get("hire_type"), ""));
            item.put("sourcePlatform", Objects.toString(job.get("source_platform"), ""));
            item.set("techStacks", mapper.valueToTree(stacks.getOrDefault(recommendation.jobId(), List.of())));
            item.put("supplemented", !originalIds.contains(recommendation.jobId()));
            String value = feedback.get(recommendation.jobId());
            if (value != null) item.put("feedback", value);
            if (value != null && !"MATCHED".equals(value)) excluded.add(item);
            else if (visible.size() < 8) { item.put("rank", visible.size() + 1); visible.add(item); }
        }
        result.set("recommendations", visible);
        result.set("excludedRecommendations", excluded);
        // Only retain saved AI reviews for the same, still-visible original jobs.
        if (result.path("aiReview").isObject()) {
            var visibleIds = new HashSet<Long>();
            visible.forEach(j -> visibleIds.add(j.path("jobId").asLong()));
            var reviews = mapper.createArrayNode();
            result.path("aiReview").path("jobs").forEach(j -> {
                if (visibleIds.contains(j.path("jobId").asLong()) && originalIds.contains(j.path("jobId").asLong())) reviews.add(j);
            });
            ((ObjectNode) result.path("aiReview")).set("jobs", reviews);
        }
        ObjectNode status = result.putObject("recommendationStatus");
        status.put("asOf", today.toString());
        status.put("candidateCount", pool.size());
        status.put("excludedCount", excluded.size());
        status.put("unavailableSavedCount", result.path("unavailableRecommendationCount").asInt());
        status.put("roleSource", roleSource);
        status.set("categories", mapper.valueToTree(preferences.category()));
        status.put("reason", !visible.isEmpty() ? "AVAILABLE"
                : skills.isEmpty() && preferences.category().isEmpty() ? "MISSING_CRITERIA"
                : !excluded.isEmpty() ? "ALL_EXCLUDED"
                : result.path("unavailableRecommendationCount").asInt() > 0 ? "SAVED_JOBS_UNAVAILABLE" : "NO_CURRENT_MATCHES");
        return result;
    }

    @Transactional
    public void currentRecommendationFeedback(String subject, long id, long jobId, String type) {
        if (!Set.of("MATCHED", "NOT_INTERESTED").contains(Objects.toString(type, ""))) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK");
        }
        long user = members.requireCurrentMember(subject).getId();
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user).isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND");
        }
        ObjectNode result = currentRecommendationsOwned(user, id);
        boolean found = false;
        for (String field : List.of("recommendations", "excludedRecommendations")) {
            for (var job : result.path(field)) if (job.path("jobId").asLong() == jobId) found = true;
        }
        if (!found) throw failure(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND");
        jdbc.update("""
                INSERT INTO ai_recommendation_feedback(analysis_id,job_post_id,feedback_type) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE feedback_type=VALUES(feedback_type),updated_at=CURRENT_TIMESTAMP(6)
                """, id, jobId, type);
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
        if (type == null || !Set.of("MATCHED", "CAREER_MISMATCH", "SKILL_MISMATCH", "LOCATION_MISMATCH",
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
        for (JsonNode recommendation : result.path("excludedRecommendations")) {
            if (recommendation.path("jobId").asLong() == jobId) found = true;
        }
        if (!found) throw failure(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND");
        jdbc.update("""
                INSERT INTO ai_recommendation_feedback(analysis_id,job_post_id,feedback_type) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE feedback_type=VALUES(feedback_type),updated_at=CURRENT_TIMESTAMP(6)
                """, id, jobId, type);
    }

    @Transactional
    public void findingFeedback(String subject, long id, String findingId, String type) {
        if (type == null || !Set.of("OPEN", "APPLIED", "NOT_APPLICABLE", "INCORRECT").contains(type)) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK");
        }
        long user = members.requireCurrentMember(subject).getId();
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user).isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND");
        }
        var rows = jdbc.queryForList("SELECT result_json FROM ai_analysis_request WHERE id=? AND user_id=? FOR UPDATE", String.class, id, user);
        if (rows.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND");
        ObjectNode stored = (ObjectNode) read(rows.getFirst());
        boolean found = false;
        for (String section : List.of("review", "aiReview")) {
            for (JsonNode finding : stored.path(section).path("findings")) {
                if (finding.path("id").asText().equals(findingId)) {
                    ((ObjectNode) finding).put("feedback", type); found = true;
                }
            }
        }
        if (!found) throw failure(HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND");
        jdbc.update("UPDATE ai_analysis_request SET result_json=? WHERE id=? AND user_id=?", json(stored), id, user);
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
        var excluded = mapper.createArrayNode();
        int unavailable = 0;
        // Recheck live visibility on every read, including cached and idempotent responses.
        for (JsonNode recommendation : result.path("recommendations")) {
            long jobId = recommendation.path("jobId").asLong();
            var jobs = jdbc.queryForList("""
                    SELECT title,company_name,location,experience,end_date FROM job_posts
                    WHERE id=? AND is_active=1 AND moderation_status='ACTIVE' AND (end_date IS NULL OR end_date>=?)
                    """, jobId, LocalDate.now(ZoneId.of("Asia/Seoul")));
            if (jobs.isEmpty()) { unavailable++; continue; }
            ObjectNode item = (ObjectNode) recommendation.deepCopy();
            var job = jobs.getFirst();
            item.put("title", Objects.toString(job.get("title"), ""));
            item.put("companyName", Objects.toString(job.get("company_name"), ""));
            item.put("location", Objects.toString(job.get("location"), ""));
            item.put("experience", Objects.toString(job.get("experience"), ""));
            item.put("endDate", Objects.toString(job.get("end_date"), ""));
            var feedback = jdbc.queryForList("SELECT feedback_type FROM ai_recommendation_feedback WHERE analysis_id=? AND job_post_id=?",
                    String.class, id, jobId);
            if (!feedback.isEmpty()) {
                item.put("feedback", feedback.getFirst());
                if (!"MATCHED".equals(feedback.getFirst())) {
                    excluded.add(item);
                    continue;
                }
            }
            visible.add(item);
        }
        result.set("recommendations", visible);
        result.set("excludedRecommendations", excluded);
        result.put("unavailableRecommendationCount", unavailable);
        if (result.path("aiReview").isObject()) {
            var visibleIds = new HashSet<Long>(); visible.forEach(j -> visibleIds.add(j.path("jobId").asLong()));
            var aiJobs = mapper.createArrayNode();
            result.path("aiReview").path("jobs").forEach(j -> { if (visibleIds.contains(j.path("jobId").asLong())) aiJobs.add(j); });
            ((ObjectNode) result.path("aiReview")).set("jobs", aiJobs);
        }
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
        var categories = preferences.category();
        if (tagIds.isEmpty() && categories.isEmpty()) return titleCandidates(skills, today);
        String placeholders = tagIds.isEmpty() ? "NULL" : String.join(",", Collections.nCopies(tagIds.size(), "?"));
        String match = "(SELECT COUNT(*) FROM post_tags p WHERE p.job_post_id=j.id AND p.tech_stack_id IN (" + placeholders + "))";
        String sql = "SELECT j.id,CONCAT_WS(' ',j.job_category,j.title) role_text,j.experience,j.location,j.hire_type,DATE(j.created_at) created_date,"
                + match + " skill_hits FROM job_posts j WHERE j.is_active=1 AND j.moderation_status='ACTIVE' AND (j.end_date IS NULL OR j.end_date>=?)"
                + " HAVING skill_hits>0 OR (" + (categories.isEmpty() ? "FALSE"
                    : String.join(" OR ", Collections.nCopies(categories.size(), "REGEXP_LIKE(role_text, ?, 'i')"))) + ")"
                + " ORDER BY skill_hits DESC,j.created_at DESC,j.id ASC LIMIT 100";
        var args = new ArrayList<Object>(tagIds);
        args.add(today);
        categories.forEach(category -> args.add(RecommendationRoles.sqlPattern(category)));
        var candidates = jdbc.query(sql, (row, index) -> new LocalRecommendationEngine.Candidate(row.getLong("id"),
                row.getString("role_text"), row.getString("experience"), row.getString("location"), row.getString("hire_type"),
                row.getDate("created_date") == null ? null : row.getDate("created_date").toLocalDate(), new ArrayList<>()), args.toArray());
        if (candidates.isEmpty()) return titleCandidates(skills, today);
        var ids = candidates.stream().map(LocalRecommendationEngine.Candidate::id).toList();
        var tags = jdbc.query("SELECT p.job_post_id,t.stack_name FROM post_tags p JOIN tech_stacks t ON t.id=p.tech_stack_id WHERE p.job_post_id IN ("
                + String.join(",", Collections.nCopies(ids.size(), "?")) + ") ORDER BY p.job_post_id,t.stack_name",
                (row, index) -> Map.entry(row.getLong("job_post_id"), row.getString("stack_name")), ids.toArray());
        var byId = new HashMap<Long, LocalRecommendationEngine.Candidate>();
        candidates.forEach(candidate -> byId.put(candidate.id(), candidate));
        tags.forEach(tag -> byId.get(tag.getKey()).skills().add(tag.getValue()));
        for (var candidate : titleCandidates(skills, today)) {
            if (!byId.containsKey(candidate.id())) candidates.add(candidate);
        }
        return candidates;
    }

    private List<LocalRecommendationEngine.Candidate> titleCandidates(Set<String> skills, LocalDate today) {
        if (skills.isEmpty()) return List.of();
        var terms = new TreeSet<String>();
        for (String skill : skills) {
            if (skill.length() < 2) continue;
            terms.add(skill);
            switch (skill) {
                case "springboot" -> terms.add("spring boot");
                case "nodejs" -> terms.add("node.js");
                case "javascript" -> terms.add("js");
                case "typescript" -> terms.add("ts");
                default -> { }
            }
        }
        if (terms.isEmpty()) return List.of();
        String conditions = String.join(" OR ", Collections.nCopies(terms.size(), "REGEXP_LIKE(j.title, ?, 'i')"));
        var args = new ArrayList<Object>(); args.add(today);
        terms.forEach(term -> {
            var literal = new StringBuilder();
            for (char character : term.toCharArray()) {
                if ("\\.^$|?*+()[]{}".indexOf(character) >= 0) literal.append('\\');
                literal.append(character);
            }
            args.add("(^|[^[:alnum:]_+#])" + literal + "([^[:alnum:]_+#]|$)");
        });
        var vocabulary = new ArrayList<>(jdbc.queryForList("SELECT stack_name FROM tech_stacks", String.class));
        vocabulary.addAll(terms);
        var rows = jdbc.queryForList("SELECT j.id,j.title,j.job_category,j.experience,j.location,j.hire_type,DATE(j.created_at) created_date"
                + " FROM job_posts j WHERE j.is_active=1 AND j.moderation_status='ACTIVE' AND (j.end_date IS NULL OR j.end_date>=?)"
                + " AND NOT EXISTS (SELECT 1 FROM post_tags p WHERE p.job_post_id=j.id) AND (" + conditions + ")"
                + " ORDER BY j.created_at DESC,j.id ASC LIMIT 100", args.toArray());
        var result = new ArrayList<LocalRecommendationEngine.Candidate>();
        for (var row : rows) {
            var mentioned = SavedResumeAnalysisInput.findSkills(row.get("title").toString(), vocabulary);
            if (Collections.disjoint(mentioned, skills)) continue;
            result.add(new LocalRecommendationEngine.Candidate(((Number) row.get("id")).longValue(),
                    Objects.toString(row.get("job_category"), ""), Objects.toString(row.get("experience"), ""),
                    Objects.toString(row.get("location"), ""), Objects.toString(row.get("hire_type"), ""),
                    row.get("created_date") == null ? null : ((java.sql.Date) row.get("created_date")).toLocalDate(),
                    new ArrayList<>(mentioned), true));
        }
        return result;
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
