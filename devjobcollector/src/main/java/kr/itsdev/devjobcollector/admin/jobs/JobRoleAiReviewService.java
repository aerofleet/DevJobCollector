package kr.itsdev.devjobcollector.admin.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.ai.JobRoleAiInput;
import kr.itsdev.devjobcollector.ai.JobRoleAiCatalog;
import kr.itsdev.devjobcollector.ai.JobRoleAiProcessor;
import kr.itsdev.devjobcollector.ai.JobRoleAiRules;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobRoleAiReviewService {
    private final JobPostRepository jobs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final JobRoleAiProcessor processor;

    public JobRoleAiReviewService(JobPostRepository jobs, JdbcTemplate jdbc, ObjectMapper mapper, JobRoleAiProcessor processor) {
        this.jobs = jobs; this.jdbc = jdbc; this.mapper = mapper; this.processor = processor;
    }

    public Map<String, Object> analyze(long jobId, AdminPrincipal actor) {
        authorize(actor);
        return Map.of("status", processor.process(jobId).name());
    }

    public List<Map<String, Object>> history(long jobId, AdminPrincipal actor) {
        authorize(actor);
        if (!jobs.existsById(jobId)) throw error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND");
        return jdbc.queryForList("SELECT id,job_version,input_hash,catalog_version,policy_version,model,result_mode,suggested_role,source_evidence,review_status,created_at,reviewed_by,reviewed_at FROM job_role_ai_proposals WHERE job_post_id=? ORDER BY id DESC LIMIT 20", jobId);
    }

    @Transactional
    public Map<String, Object> approve(long jobId, long proposalId, long expectedVersion, String expectedHash, AdminPrincipal actor) {
        authorize(actor);
        if (expectedHash == null || !expectedHash.matches("[0-9a-f]{64}")) throw error(HttpStatus.BAD_REQUEST, "INVALID_INPUT_HASH");
        var job = jobs.findByIdForModeration(jobId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.getVersion() != expectedVersion) throw error(HttpStatus.CONFLICT, "JOB_CHANGED");
        var rows = jdbc.queryForList("SELECT job_version,input_hash,catalog_version,catalog_snapshot,policy_version,model,result_mode,suggested_role,source_evidence,review_status FROM job_role_ai_proposals WHERE id=? AND job_post_id=? FOR UPDATE", proposalId, jobId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "ROLE_PROPOSAL_NOT_FOUND");
        var row = rows.getFirst();
        if (!"PENDING_REVIEW".equals(row.get("review_status")) || !JobRoleAiRules.VERSION.equals(row.get("policy_version"))
                || ((Number) row.get("job_version")).longValue() != expectedVersion || !expectedHash.equals(row.get("input_hash")))
            throw error(HttpStatus.CONFLICT, "ROLE_PROPOSAL_STALE");
        String newRole = null;
        try {
            var roles = JobRoleAiCatalog.decodeSnapshot(mapper, row.get("catalog_snapshot"));
            var input = JobRoleAiInput.prepare(jobId, job.getSourcePlatform().name(), job.getTitle(), job.getJobCategory(), job.getApplyQual(),
                    (String) row.get("catalog_version"), (String) row.get("model"), roles);
            if (!expectedHash.equals(input.inputHash())) throw error(HttpStatus.CONFLICT, "ROLE_PROPOSAL_STALE");
            var result = JobRoleAiRules.requirePricingEvidence(JobRoleAiRules.validate(mapper.valueToTree(Map.of("mode", row.get("result_mode"),
                    "role", row.get("suggested_role"), "evidence", row.get("source_evidence"))), input.evidenceSource(), roles));
            if ("ABSTAIN".equals(result.mode())) throw error(HttpStatus.CONFLICT, "ROLE_PROPOSAL_STALE");
            if ("NEW".equals(result.mode())) newRole = result.role();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw error(HttpStatus.CONFLICT, "ROLE_PROPOSAL_STALE");
        }
        if (newRole != null) jdbc.update("INSERT INTO job_role_ai_catalog (role_label,approved_by,approved_at) VALUES (?,?,NOW(6)) ON DUPLICATE KEY UPDATE role_label=role_label", newRole, actor.id());
        int updated = jdbc.update("UPDATE job_role_ai_proposals SET review_status='APPROVED',reviewed_by=?,reviewed_at=NOW(6) WHERE id=? AND job_post_id=? AND review_status='PENDING_REVIEW'", actor.id(), proposalId, jobId);
        if (updated != 1) throw error(HttpStatus.CONFLICT, "ROLE_PROPOSAL_STALE");
        return Map.of("status", "APPROVED", "proposalId", proposalId);
    }

    @Transactional
    public Map<String, Object> reject(long jobId, long proposalId, long expectedVersion, String expectedHash, AdminPrincipal actor) {
        authorize(actor);
        if (expectedHash == null || !expectedHash.matches("[0-9a-f]{64}")) throw error(HttpStatus.BAD_REQUEST, "INVALID_INPUT_HASH");
        var job = jobs.findByIdForModeration(jobId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.getVersion() != expectedVersion) throw error(HttpStatus.CONFLICT, "JOB_CHANGED");
        var rows = jdbc.queryForList("SELECT job_version,input_hash,review_status FROM job_role_ai_proposals WHERE id=? AND job_post_id=? FOR UPDATE", proposalId, jobId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "ROLE_PROPOSAL_NOT_FOUND");
        var row = rows.getFirst();
        if (!"PENDING_REVIEW".equals(row.get("review_status")) || ((Number) row.get("job_version")).longValue() != expectedVersion
                || !expectedHash.equals(row.get("input_hash"))) throw error(HttpStatus.CONFLICT, "ROLE_PROPOSAL_STALE");
        int updated = jdbc.update("UPDATE job_role_ai_proposals SET review_status='REJECTED',reviewed_by=?,reviewed_at=NOW(6) WHERE id=? AND job_post_id=? AND review_status='PENDING_REVIEW'", actor.id(), proposalId, jobId);
        if (updated != 1) throw error(HttpStatus.CONFLICT, "ROLE_PROPOSAL_STALE");
        return Map.of("status", "REJECTED", "proposalId", proposalId);
    }

    private static void authorize(AdminPrincipal actor) {
        if (actor == null || actor.id() == null || (actor.role() != AdminRole.ADMIN && actor.role() != AdminRole.SUPER_ADMIN))
            throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
    }

    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
}
