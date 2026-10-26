package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit user-reviewed text transmission. Does not mark the heuristic PII gate as passed. */
@Service
public class ResumeContentReviewService {
    public static final String CONSENT = "openai-content-review-v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentMemberService members;
    private final ResumeAnalysisService analyses;
    private final SavedResumeAnalysisInput inputs;
    private final ResumePiiGate pii;
    private final OpenAiEvidenceClient client;
    private final TransactionTemplate tx;
    public ResumeContentReviewService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentMemberService members,
            ResumeAnalysisService analyses, SavedResumeAnalysisInput inputs, ResumePiiGate pii, OpenAiEvidenceClient client,
            PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.mapper=mapper; this.members=members; this.analyses=analyses; this.inputs=inputs;
        this.pii=pii; this.client=client; this.tx=new TransactionTemplate(manager);
    }
    record Prepared(long resumeId, SavedResumeAnalysisInput.Prepared input, List<ResumeContentReviewRules.Source> sources,
                    List<ResumeContentReviewRules.Job> jobs, Map<Long, String> jobFingerprints, Map<String, Object> payload, String hash) {}

    public ObjectNode preview(String subject, long id) {
        Prepared prepared = prepare(subject, id);
        var result = mapper.createObjectNode(); result.put("previewHash", prepared.hash()); result.put("consentVersion", CONSENT);
        result.put("piiGatePassed", false); result.put("policy", "USER_REVIEWED_MASKED_TEXT");
        result.set("payload", mapper.valueToTree(prepared.payload()));
        var locations = result.putArray("sources");
        for (var source : prepared.sources()) {
            var row = locations.addObject(); row.put("id", source.id()); row.put("label", source.original().label());
            row.put("section", source.original().section()); if (source.original().page()!=null) row.put("page", source.original().page());
        }
        return result;
    }

    private Prepared prepare(String subject, long id) {
        ObjectNode analysis = analyses.get(subject, id);
        if (!client.available()) throw fail(HttpStatus.SERVICE_UNAVAILABLE, "CONTENT_REVIEW_UNAVAILABLE");
        long resumeId = analysis.path("resumeId").asLong();
        var input = inputs.prepare(subject, resumeId);
        if (!SavedResumeAnalysisInput.fingerprint(input).equals(analysis.path("inputFingerprint").asText()))
            throw fail(HttpStatus.CONFLICT, "ANALYSIS_INPUT_CHANGED");
        long user = members.requireCurrentMember(subject).getId();
        var contentRows = jdbc.queryForList("SELECT content_json FROM resumes WHERE id=? AND user_id=?", String.class, resumeId, user);
        if (contentRows.isEmpty() || !ResumeAnalysisService.hash(contentRows.getFirst()).equals(input.contentHash())) throw fail(HttpStatus.CONFLICT, "ANALYSIS_INPUT_CHANGED");
        var basic = read(contentRows.getFirst()).path("basicInfo");
        var known = new ArrayList<String>();
        for (String field : List.of("name", "email", "phone", "birthDate", "birthYear", "address")) known.add(basic.path(field).asText());
        var sources = new ArrayList<ResumeContentReviewRules.Source>();
        var sourcePayload = new ArrayList<Map<String, Object>>();
        int length = 0;
        var documents = new ArrayList<>(input.documents());
        if (!input.preferences().location().isEmpty()) documents.add(new ResumeEvidenceReview.Document(
                "/careerProfile/location", "저장된 이력서", null, "희망 근무 지역",
                "희망 근무 지역 (선택한 지역 중 하나, 원격 선택은 지역 범위를 넓히지 않음): " + String.join(", ", input.preferences().location())
                        + ". 희망 조건이며 거주지, 취업 허가 또는 비자 보유를 의미하지 않습니다."));
        int experienceIndex = 0;
        for (var experience : read(contentRows.getFirst()).path("experience")) {
            if (experience.path("isCurrent").asBoolean(false)) documents.add(new ResumeEvidenceReview.Document(
                    "/experience/" + experienceIndex + "/currentEmployment", "저장된 이력서", null, "경력 " + (experienceIndex + 1),
                    "현재 재직 중 (기준일: " + LocalDate.now(ZoneId.of("Asia/Seoul")) + ")"));
            experienceIndex++;
        }
        for (var document : documents) {
            if (document.text().isBlank() || document.key().matches(".*/(link|repository|company)$")) continue;
            String masked = pii.mask(document.text(), known).maskedText();
            length += masked.length();
            if (length > 40000 || sources.size() >= 120) throw fail(HttpStatus.UNPROCESSABLE_ENTITY, "CONTENT_REVIEW_TOO_LARGE");
            String sourceId = "S" + (sources.size() + 1);
            sources.add(new ResumeContentReviewRules.Source(sourceId, masked, document));
            // Filename and internal IDs never enter the external payload.
            sourcePayload.add(Map.of("sourceId", sourceId, "kind", document.key().startsWith("attachment:") ? "ATTACHMENT" : "SAVED_RESUME",
                    "section", document.section(), "field", document.key().startsWith("attachment:") ? "document" : document.key().substring(document.key().lastIndexOf('/') + 1), "text", masked,
                    "passages", ResumeContentReviewRules.passages(sourceId, masked)));
        }
        if (sources.isEmpty()) throw fail(HttpStatus.UNPROCESSABLE_ENTITY, "NO_RESUME_CONTENT");
        var jobs = new ArrayList<ResumeContentReviewRules.Job>();
        var jobPayload = new ArrayList<Map<String, Object>>();
        var jobFingerprints = new HashMap<Long, String>();
        for (var recommendation : analysis.path("recommendations")) {
            long jobId = recommendation.path("jobId").asLong();
            var rows = jdbc.queryForList("SELECT title,job_category,experience,location,hire_type,LEFT(apply_qual,5000) qualifications FROM job_posts WHERE id=? AND is_active=1 AND moderation_status='ACTIVE' AND end_date>=?", jobId, LocalDate.now(ZoneId.of("Asia/Seoul")));
            if (rows.isEmpty()) continue;
            var row = rows.getFirst();
            jobFingerprints.put(jobId, AiJobFit.jobFingerprint(row));
            String text = "제목: " + Objects.toString(row.get("title"), "") + "\n직무: " + Objects.toString(row.get("job_category"), "")
                    + "\n경력: " + Objects.toString(row.get("experience"), "") + "\n지역: " + Objects.toString(row.get("location"), "")
                    + "\n고용 형태: " + Objects.toString(row.get("hire_type"), "") + "\n수집된 자격 요건(최대 5000자): " + Objects.toString(row.get("qualifications"), "미수집")
                    + "\n기술명(필수 요건 분류 아님): " + recommendation.path("matchPoints") + " " + recommendation.path("missingPoints");
            text = pii.mask(text.replaceAll("<[^>]*>", " "), known).maskedText();
            int slot = jobs.size(); jobs.add(new ResumeContentReviewRules.Job(slot, jobId, text));
            jobPayload.add(Map.of("slot", slot, "text", text, "passages", ResumeContentReviewRules.passages("J" + slot, text)));
        }
        Map<String, Object> payload = Map.of("sources", sourcePayload, "jobs", jobPayload);
        String hash = ResumeAnalysisService.hash(id + ":" + CONSENT + ":" + ResumeContentReviewRules.VERSION + ":" + SavedResumeAnalysisInput.fingerprint(input) + ":" + json(payload));
        return new Prepared(resumeId, input, List.copyOf(sources), List.copyOf(jobs), Map.copyOf(jobFingerprints), payload, hash);
    }

    public ObjectNode review(String subject, long id, String consent, String previewHash) {
        if (!CONSENT.equals(consent)) throw fail(HttpStatus.BAD_REQUEST, "CONTENT_REVIEW_CONSENT_REQUIRED");
        ObjectNode existing = get(subject, id);
        if (!existing.path("status").asText().equals("NOT_REQUESTED") && existing.path("previewHash").asText().equals(previewHash)) {
            return existing;
        }
        if (existing.path("status").asText().equals("PENDING")) throw fail(HttpStatus.CONFLICT, "REVIEW_ALREADY_REQUESTED");
        Prepared prepared = prepare(subject, id);
        if (!prepared.hash().equals(previewHash)) throw fail(HttpStatus.CONFLICT, "PREVIEW_CHANGED");
        long user = members.requireCurrentMember(subject).getId();
        Boolean reserved = tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
            ObjectNode stored = ownedRaw(user, id);
            if (stored.has("aiReview")) {
                if (stored.path("aiReview").path("previewHash").asText().equals(previewHash)) return false;
                if (stored.path("aiReview").path("status").asText().equals("PENDING")) throw fail(HttpStatus.CONFLICT, "REVIEW_ALREADY_REQUESTED");
            }
            var content = jdbc.queryForList("SELECT content_json FROM resumes WHERE id=? AND user_id=? FOR SHARE", String.class, prepared.resumeId(), user);
            var ids = jdbc.queryForList("SELECT id FROM resume_attachments WHERE resume_id=? ORDER BY id FOR SHARE", Long.class, prepared.resumeId());
            if (content.isEmpty() || !ResumeAnalysisService.hash(content.getFirst()).equals(prepared.input().contentHash()) || !ids.equals(prepared.input().attachmentIds()))
                throw fail(HttpStatus.CONFLICT, "ANALYSIS_INPUT_CHANGED");
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            jdbc.update("INSERT IGNORE INTO ai_external_daily_usage(user_id,usage_date,attempt_count) VALUES(?,?,0)",user,today);
            if (jdbc.update("UPDATE ai_external_daily_usage SET attempt_count=attempt_count+1 WHERE user_id=? AND usage_date=? AND attempt_count<5",user,today)!=1)
                throw fail(HttpStatus.TOO_MANY_REQUESTS, "DAILY_CONTENT_REVIEW_LIMIT");
            jdbc.update("INSERT IGNORE INTO ai_external_global_usage(usage_date,attempt_count) VALUES(?,0)",today);
            if (jdbc.update("UPDATE ai_external_global_usage SET attempt_count=attempt_count+1 WHERE usage_date=? AND attempt_count<100",today)!=1)
                throw fail(HttpStatus.TOO_MANY_REQUESTS, "GLOBAL_CONTENT_REVIEW_LIMIT");
            ObjectNode claim = stored.putObject("aiReview"); claim.put("status", "PENDING"); claim.put("createdAt", Instant.now().toString());
            claim.put("consentVersion", CONSENT); claim.put("previewHash", previewHash); claim.put("policy", "USER_REVIEWED_MASKED_TEXT");
            claim.put("piiGatePassed", false); claim.put("externalAttemptCount", 1);
            claim.put("model", OpenAiEvidenceClient.CONTENT_MODEL);
            claim.put("fitVersion", AiJobFit.VERSION);
            jdbc.update("UPDATE ai_analysis_request SET result_json=? WHERE id=? AND user_id=?", json(stored), id, user);
            return true;
        });
        if (Boolean.TRUE.equals(reserved)) {
            ObjectNode reviewed = null;
            try {
                reviewed = ResumeContentReviewRules.validate(client.review(prepared.payload()), prepared.sources(), prepared.jobs(), mapper);
                for (var job : reviewed.path("jobs")) {
                    ((ObjectNode)job).put("fitVersion", AiJobFit.VERSION)
                            .put("inputFingerprint", SavedResumeAnalysisInput.fingerprint(prepared.input()))
                            .put("jobFingerprint", prepared.jobFingerprints().get(job.path("jobId").asLong()));
                }
            }
            catch (Exception ignored) { /* Never persist provider error bodies or invalid output. No automatic retry. */ }
            ObjectNode output = reviewed;
            tx.executeWithoutResult(status -> {
                var rows = jdbc.queryForList("SELECT result_json FROM ai_analysis_request WHERE id=? AND user_id=? FOR UPDATE", String.class, id, user);
                if (rows.isEmpty()) return; // Deletion during a provider call must not recreate the result.
                ObjectNode stored = (ObjectNode) read(rows.getFirst());
                ObjectNode claim = (ObjectNode) stored.path("aiReview");
                if (!claim.path("status").asText().equals("PENDING")) return;
                if (output != null) claim.setAll(output);
                claim.put("status", output == null ? "FAILED" : "COMPLETED"); claim.put("completedAt", Instant.now().toString());
                jdbc.update("UPDATE ai_analysis_request SET result_json=? WHERE id=? AND user_id=?", json(stored), id, user);
            });
        }
        return get(subject, id);
    }

    public ObjectNode get(String subject, long id) {
        ObjectNode analysis = analyses.get(subject, id);
        ObjectNode result = analysis.has("aiReview") ? ((ObjectNode) analysis.path("aiReview")).deepCopy() : mapper.createObjectNode().put("status", "NOT_REQUESTED");
        if (result.path("status").asText().equals("PENDING") && Instant.parse(result.path("createdAt").asText()).isBefore(Instant.now().minusSeconds(120))) result.put("status", "INTERRUPTED");
        if (result.has("jobs")) {
            var visible = new HashSet<Long>(); analysis.path("recommendations").forEach(j -> visible.add(j.path("jobId").asLong()));
            var jobs = mapper.createArrayNode(); result.path("jobs").forEach(j -> { if (visible.contains(j.path("jobId").asLong())) jobs.add(j); });
            result.set("jobs", jobs);
        }
        if ("COMPLETED".equals(result.path("status").asText())) {
            long user = members.requireCurrentMember(subject).getId();
            String content = jdbc.queryForObject("SELECT content_json FROM resumes WHERE id=? AND user_id=?", String.class, analysis.path("resumeId").asLong(), user);
            var attachments = jdbc.queryForList("SELECT id FROM resume_attachments WHERE resume_id=? ORDER BY id", Long.class, analysis.path("resumeId").asLong());
            String fingerprint = ResumeAnalysisService.hash(ResumeAnalysisService.hash(content) + ":" + attachments);
            boolean stale = !AiJobFit.VERSION.equals(result.path("fitVersion").asText());
            for (var job : result.path("jobs")) {
                var metadata = jdbc.queryForList("SELECT title,job_category,experience,location,hire_type,LEFT(apply_qual,5000) qualifications FROM job_posts WHERE id=?", job.path("jobId").asLong());
                if (metadata.isEmpty() || "REVIEW_REQUIRED".equals(AiJobFit.decision(job, fingerprint, AiJobFit.jobFingerprint(metadata.getFirst())))) {
                    stale = true;
                    if (job.has("decision")) ((ObjectNode)job).put("decision", "REVIEW_REQUIRED").put("decisionReason", "자료가 변경되어 적합성을 다시 검토해야 합니다.");
                }
            }
            result.put("fitReviewRequired", stale);
        }
        return result;
    }
    public ObjectNode confirm(String subject, long id, String findingId, int option) {
        long user = members.requireCurrentMember(subject).getId();
        tx.executeWithoutResult(status -> {
            ObjectNode stored = ownedRaw(user,id);
            if (!stored.path("aiReview").path("status").asText().equals("COMPLETED")) throw fail(HttpStatus.CONFLICT,"REVIEW_NOT_READY");
            try { ResumeCoachingRules.answer((ObjectNode) stored.path("aiReview"),findingId,option); }
            catch (IllegalArgumentException error) { throw fail(HttpStatus.BAD_REQUEST,"INVALID_CONFIRMATION"); }
            jdbc.update("UPDATE ai_analysis_request SET result_json=? WHERE id=? AND user_id=?",json(stored),id,user);
        });
        return get(subject,id);
    }
    private ObjectNode ownedRaw(long user, long id) {
        var rows = jdbc.queryForList("SELECT result_json FROM ai_analysis_request WHERE id=? AND user_id=? FOR UPDATE",String.class,id,user);
        if (rows.isEmpty()) throw fail(HttpStatus.NOT_FOUND,"ANALYSIS_NOT_FOUND");
        return (ObjectNode) read(rows.getFirst());
    }
    private String json(Object value) {
        try { return mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Invalid review data"); }
    }
    private JsonNode read(String value) {
        try { return mapper.readTree(value); } catch (Exception error) { throw new IllegalStateException("Invalid review data"); }
    }
    private static org.springframework.web.server.ResponseStatusException fail(HttpStatus status, String code) { return ResumeTextExtractor.failure(status, code); }
}
