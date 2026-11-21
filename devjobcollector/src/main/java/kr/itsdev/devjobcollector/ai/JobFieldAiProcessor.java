package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Generates evidence-backed field proposals, never approvals or public tags. */
@Service
public class JobFieldAiProcessor {
    private final JobPostRepository jobs;
    private final OpenAiEvidenceClient client;
    private final JobRoleAiBudget budget;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Set<String> sources;

    public JobFieldAiProcessor(JobPostRepository jobs, OpenAiEvidenceClient client, JobRoleAiBudget budget,
            JdbcTemplate jdbc, ObjectMapper mapper, @Value("${ai.job-field.enabled:false}") boolean enabled,
            @Value("${ai.job-field.allowed-sources:}") String sources) {
        this.jobs = jobs; this.client = client; this.budget = budget; this.jdbc = jdbc; this.mapper = mapper; this.enabled = enabled;
        this.sources = Arrays.stream(sources.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public Outcome process(long id) {
        if (!enabled || !client.available()) return Outcome.DISABLED;
        JobPost job = jobs.findById(id).orElse(null);
        if (!eligible(job)) return Outcome.NOT_ELIGIBLE;
        JobRoleAiInput.Prepared input;
        try { input = prepare(job, false); } catch (IllegalArgumentException invalid) { return Outcome.INPUT_UNUSABLE; }
        if (budget.alreadyClaimed(id, input.inputHash())) return Outcome.ALREADY_CLAIMED;
        int tokens = client.countJobFieldInputTokens(input.title(), input.category(), input.content());
        if (tokens > 2000) {
            try { input = prepare(job, true); } catch (IllegalArgumentException invalid) { return Outcome.INPUT_UNUSABLE; }
            if (budget.alreadyClaimed(id, input.inputHash())) return Outcome.ALREADY_CLAIMED;
            tokens = client.countJobFieldInputTokens(input.title(), input.category(), input.content());
            if (tokens > 2000) return Outcome.INPUT_UNUSABLE;
        }
        JobPost latest = jobs.findById(id).orElse(null);
        if (!eligible(latest) || latest.getVersion() != job.getVersion()) return Outcome.JOB_CHANGED;
        try { prepareMatching(latest, input.inputHash()); } catch (IllegalArgumentException invalid) { return Outcome.JOB_CHANGED; }
        var reservation = budget.reserveField(id, input.inputHash(), tokens);
        if (reservation == JobRoleAiBudget.Reservation.ALREADY_CLAIMED) return Outcome.ALREADY_CLAIMED;
        if (reservation == JobRoleAiBudget.Reservation.DAILY_LIMIT) return Outcome.DAILY_LIMIT;
        try {
            var result = client.suggestJobFields(input.title(), input.category(), input.content());
            var fields = JobFieldAiPolicy.validate(result, input.evidenceSource());
            String json = mapper.writeValueAsString(result);
            int inserted = jdbc.update("""
                    INSERT INTO job_field_ai_proposals (job_post_id,job_version,input_hash,policy_version,model,fields_json,review_status,created_at)
                    SELECT id,version,?,?,?,?,?,NOW(6) FROM job_posts WHERE id=? AND version=?
                    """, input.inputHash(), JobFieldAiPolicy.VERSION, OpenAiEvidenceClient.CONTENT_MODEL, json,
                    fields.isEmpty() ? "ABSTAINED" : "PENDING_REVIEW", id, job.getVersion());
            return inserted == 1 ? Outcome.STORED : Outcome.JOB_CHANGED;
        } catch (DuplicateKeyException duplicate) {
            return Outcome.ALREADY_CLAIMED;
        } catch (RuntimeException | JsonProcessingException failure) {
            try { budget.releaseFailedClaim(id, input.inputHash()); } catch (RuntimeException release) { failure.addSuppressed(release); }
            throw new IllegalStateException("JOB_FIELD_GENERATION_FAILED", failure);
        }
    }

    private boolean eligible(JobPost job) {
        if (job == null || !job.isActive() || job.getModerationStatus() != JobModerationStatus.ACTIVE
                || job.getEndDate() == null || job.getEndDate().isBefore(LocalDate.now()) || job.getSourcePlatform() == null) return false;
        if (job.getSourcePlatform() == SourcePlatform.PUBLIC_ALIO) return sources.contains("PUBLIC_ALIO:PUBLIC_ALIO");
        String original = job.getOriginalSn();
        if (original == null || original.isBlank() || original.indexOf(':') == 0) return false;
        int delimiter = original.indexOf(':');
        String sourceId = delimiter < 0 ? job.getSourcePlatform().name() : original.substring(0, delimiter);
        return sources.contains(job.getSourcePlatform().name() + ":" + sourceId);
    }

    static JobRoleAiInput.Prepared prepare(JobPost job, boolean compact) {
        String category = job.getJobCategory() == null ? "" : job.getJobCategory();
        if (job.getHireType() != null && !job.getHireType().isBlank()) category += "\n고용 형태: " + job.getHireType().trim();
        String raw = job.getApplyQual();
        if (raw == null || raw.isBlank()) raw = job.getTitle() + "\n" + (job.getJobCategory() == null ? "" : job.getJobCategory());
        return compact ? JobRoleAiInput.prepareCompact(job.getId(), job.getSourcePlatform().name(), job.getTitle(), category, raw,
                JobFieldAiPolicy.VERSION, OpenAiEvidenceClient.CONTENT_MODEL, JobRoleAiRules.EXISTING_ROLES)
                : JobRoleAiInput.prepare(job.getId(), job.getSourcePlatform().name(), job.getTitle(), category, raw,
                JobFieldAiPolicy.VERSION, OpenAiEvidenceClient.CONTENT_MODEL, JobRoleAiRules.EXISTING_ROLES);
    }

    public static JobRoleAiInput.Prepared prepareMatching(JobPost job, String hash) {
        var original = prepare(job, false);
        if (original.inputHash().equals(hash)) return original;
        var compact = prepare(job, true);
        if (compact.inputHash().equals(hash)) return compact;
        throw new IllegalArgumentException("JOB_FIELD_INPUT_CHANGED");
    }

    public enum Outcome { DISABLED, NOT_ELIGIBLE, INPUT_UNUSABLE, JOB_CHANGED, ALREADY_CLAIMED, DAILY_LIMIT, STORED }
}
