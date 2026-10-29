package kr.itsdev.devjobcollector.admin.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.ai.OpenAiEvidenceClient;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@Service
public class JobMetadataAiService {
    private final JobPostRepository jobs;
    private final OpenAiEvidenceClient ai;
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final Set<String> allowedSources;

    public JobMetadataAiService(JobPostRepository jobs, OpenAiEvidenceClient ai, JdbcTemplate jdbc,
            @Value("${ai.job-metadata.enabled:false}") boolean enabled,
            @Value("${ai.job-metadata.allowed-sources:}") String allowedSources) {
        this.jobs = jobs; this.ai = ai; this.jdbc = jdbc; this.enabled = enabled;
        this.allowedSources = Arrays.stream(allowedSources.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public Preview preview(long id, AdminPrincipal actor) {
        authorize(actor);
        return preview(jobs.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND")));
    }

    public List<Map<String, Object>> history(long id, AdminPrincipal actor) {
        authorize(actor);
        if (!jobs.existsById(id)) throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
        return jdbc.queryForList("SELECT id,job_version,input_hash,source_key,model,experience_suggestion,experience_quote,location_suggestion,location_quote,review_status,created_at,reviewed_by,reviewed_at,applied_experience_category,applied_experience_label,applied_location FROM job_metadata_ai_review WHERE job_post_id=? ORDER BY id DESC LIMIT 20", id);
    }

    public Map<String, Object> analyze(long id, String expectedHash, boolean confirmed, AdminPrincipal actor) {
        authorize(actor);
        if (!confirmed) throw error(HttpStatus.BAD_REQUEST, "PREVIEW_CONFIRMATION_REQUIRED");
        if (!enabled || !ai.available()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "JOB_AI_DISABLED");
        JobPost job = jobs.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        Preview preview = preview(job);
        if (!allowedSources.contains(preview.sourceKey())) throw error(HttpStatus.FORBIDDEN, "SOURCE_NOT_ALLOWED");
        boolean experienceUnknown = "UNKNOWN".equals(job.getExperienceCategory());
        boolean locationUnknown = job.getLocation() == null || job.getLocation().isBlank()
                || job.getLocation().contains("확인 필요") || job.getLocation().contains("미정");
        if (!experienceUnknown && !locationUnknown) throw error(HttpStatus.CONFLICT, "JOB_METADATA_ALREADY_KNOWN");
        if (expectedHash == null || !MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8), preview.inputHash().getBytes(StandardCharsets.UTF_8)))
            throw error(HttpStatus.CONFLICT, "PREVIEW_CHANGED");
        JsonNode result = ai.suggestJobMetadata(preview.content());
        String experience = checked(result, "experience", 255);
        String experienceQuote = checked(result, "experienceQuote", 1000);
        String location = checked(result, "location", 255);
        String locationQuote = checked(result, "locationQuote", 1000);
        validatePair(preview.content(), experience, experienceQuote);
        validatePair(preview.content(), location, locationQuote);
        int inserted = jdbc.update("INSERT INTO job_metadata_ai_review (job_post_id,job_version,input_hash,source_key,model,experience_suggestion,experience_quote,location_suggestion,location_quote,review_status,requested_by,created_at) SELECT ?,?,?,?,?,?,?,?,?,?,?,NOW(6) FROM job_posts WHERE id=? AND version=?",
                id, preview.version(), preview.inputHash(), preview.sourceKey(), OpenAiEvidenceClient.MODEL,
                experience, experienceQuote, location, locationQuote, "NEEDS_REVIEW", actor.id(), id, preview.version());
        if (inserted != 1) throw error(HttpStatus.CONFLICT, "JOB_CHANGED");
        return Map.of("status", "NEEDS_REVIEW", "experience", experience, "experienceQuote", experienceQuote,
                "location", location, "locationQuote", locationQuote, "inputHash", preview.inputHash());
    }

    @Transactional
    public Map<String, Object> apply(long id, long reviewId, long expectedVersion, String category,
                                     String experienceLabel, String locationLabel, AdminPrincipal actor) {
        authorize(actor);
        String selectedCategory = category == null || category.isBlank() ? null : category.trim();
        String selectedExperience = experienceLabel == null || experienceLabel.isBlank() ? null : experienceLabel.trim();
        String selectedLocation = locationLabel == null || locationLabel.isBlank() ? null : locationLabel.trim();
        if ((selectedCategory == null) != (selectedExperience == null)
                || (selectedCategory != null && !Set.of("ENTRY", "EXPERIENCED", "BOTH", "ANY").contains(selectedCategory))
                || (selectedExperience != null && selectedExperience.length() > 255)
                || (selectedLocation != null && selectedLocation.length() > 255)
                || (selectedCategory == null && selectedLocation == null)) throw error(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_VALUES");
        JobPost job = jobs.findByIdForModeration(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.getVersion() != expectedVersion) throw error(HttpStatus.CONFLICT, "JOB_CHANGED");
        var reviews = jdbc.queryForList("SELECT job_version,input_hash,experience_quote,location_quote,review_status FROM job_metadata_ai_review WHERE id=? AND job_post_id=? FOR UPDATE", reviewId, id);
        if (reviews.isEmpty()) throw error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND");
        var review = reviews.getFirst();
        if (!"NEEDS_REVIEW".equals(review.get("review_status")) || ((Number) review.get("job_version")).longValue() != expectedVersion
                || !preview(job).inputHash().equals(review.get("input_hash"))) throw error(HttpStatus.CONFLICT, "REVIEW_STALE");
        String experienceQuote = String.valueOf(review.get("experience_quote"));
        String locationQuote = String.valueOf(review.get("location_quote"));
        if ((selectedCategory != null && experienceQuote.isBlank()) || (selectedLocation != null && locationQuote.isBlank()))
            throw error(HttpStatus.BAD_REQUEST, "REVIEW_EVIDENCE_REQUIRED");
        job.applyReviewedMetadata(selectedCategory, selectedExperience, selectedCategory == null ? null : experienceQuote, selectedLocation);
        jobs.saveAndFlush(job);
        int updated = jdbc.update("UPDATE job_metadata_ai_review SET review_status='APPLIED',reviewed_by=?,reviewed_at=NOW(6),applied_experience_category=?,applied_experience_label=?,applied_location=? WHERE id=? AND review_status='NEEDS_REVIEW'",
                actor.id(), selectedCategory, selectedExperience, selectedLocation, reviewId);
        if (updated != 1) throw error(HttpStatus.CONFLICT, "REVIEW_STALE");
        return Map.of("status", "APPLIED", "version", job.getVersion());
    }

    private Preview preview(JobPost job) {
        String sourceId = job.getOriginalSn();
        int delimiter = sourceId.indexOf(':');
        String sourceKey = job.getSourcePlatform().name() + ":" + (delimiter < 0 ? job.getSourcePlatform().name() : sourceId.substring(0, delimiter));
        String raw = job.getApplyQual() == null ? "" : job.getApplyQual();
        String text = Jsoup.parse(HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(raw))).text().replace('\u00a0', ' ').trim();
        if (text.isBlank()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "JOB_CONTENT_EMPTY");
        boolean truncated = text.length() > 12000;
        if (truncated) {
            int end = Character.isHighSurrogate(text.charAt(11999)) ? 11999 : 12000;
            text = text.substring(0, end);
        }
        return new Preview(job.getId(), job.getVersion(), sourceKey, text, sha256(text), allowedSources.contains(sourceKey), truncated, job.getLocation());
    }

    static void validatePair(String input, String value, String quote) {
        if (value.isEmpty() != quote.isEmpty() || (!quote.isEmpty() && !input.contains(quote)))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "AI_QUOTE_NOT_VERIFIED");
    }
    private static String checked(JsonNode result, String field, int max) {
        JsonNode node = result.path(field);
        if (!node.isTextual() || node.asText().length() > max) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "AI_OUTPUT_INVALID");
        return node.asText().trim();
    }
    private static void authorize(AdminPrincipal actor) {
        if (actor == null || actor.role() == AdminRole.REVIEWER) throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
    }
    private static String sha256(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    public record Preview(long jobId, long version, String sourceKey, String content, String inputHash, boolean sourceAllowed, boolean truncated, String sourceLocation) {}
}
