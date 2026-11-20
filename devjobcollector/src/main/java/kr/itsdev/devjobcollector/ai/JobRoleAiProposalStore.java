package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores evidence-backed proposals; this class never changes the public job or its tags. */
@Repository
public class JobRoleAiProposalStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JobRoleAiProposalStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public SaveResult save(long jobId, long expectedVersion, String catalogVersion,
                           JobRoleAiInput.Prepared input, JobRoleAiProposalService.Proposal proposal) {
        if (jobId <= 0 || expectedVersion < 0 || catalogVersion == null || catalogVersion.isBlank()
                || catalogVersion.length() > 64 || input == null || proposal == null || input.jobId() != jobId
                || !catalogVersion.equals(input.catalogVersion()) || !OpenAiEvidenceClient.CONTENT_MODEL.equals(input.model())
                || !input.inputHash().matches("[0-9a-f]{64}") || !input.inputHash().equals(proposal.inputHash()))
            throw new IllegalArgumentException("INVALID_ROLE_PROPOSAL");
        JobRoleAiRules.validate(mapper.valueToTree(Map.of("mode", proposal.mode(), "role", proposal.role(),
                "evidence", proposal.evidence())), input.evidenceSource(), java.util.Set.copyOf(input.allowedRoles()));
        String status = "ABSTAIN".equals(proposal.mode()) ? "ABSTAINED" : "PENDING_REVIEW";
        try {
            String catalogSnapshot = mapper.writeValueAsString(input.allowedRoles());
            int inserted = jdbc.update("""
                    INSERT INTO job_role_ai_proposals
                    (job_post_id, job_version, input_hash, catalog_version, catalog_snapshot, policy_version, model,
                     result_mode, suggested_role, source_evidence, review_status, created_at)
                    SELECT id, version, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6)
                    FROM job_posts WHERE id = ? AND version = ?
                    """, input.inputHash(), catalogVersion, catalogSnapshot, JobRoleAiRules.VERSION, OpenAiEvidenceClient.CONTENT_MODEL,
                    proposal.mode(), proposal.role(), proposal.evidence(), status, jobId, expectedVersion);
            return inserted == 1 ? SaveResult.STORED : SaveResult.JOB_CHANGED;
        } catch (DuplicateKeyException duplicate) {
            return SaveResult.ALREADY_EXISTS;
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("INVALID_ROLE_CATALOG");
        }
    }

    public enum SaveResult { STORED, JOB_CHANGED, ALREADY_EXISTS }
}
