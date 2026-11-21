package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.itsdev.devjobcollector.domain.JobPost;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Batched, opt-in reads of approved proposals; stale or invalid results are never returned. */
@Service
public class JobRoleAiReadService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public JobRoleAiReadService(JdbcTemplate jdbc, ObjectMapper mapper,
                                @Value("${ai.job-role.display-enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.mapper = mapper; this.enabled = enabled;
    }

    public Map<Long, String> approvedRoles(List<JobPost> jobs) {
        if (!enabled || jobs.isEmpty()) return Map.of();
        Map<Long, JobPost> byId = jobs.stream().collect(Collectors.toMap(JobPost::getId, job -> job, (first, second) -> first));
        String placeholders = byId.keySet().stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.job_post_id,p.job_version,p.input_hash,p.catalog_version,p.catalog_snapshot,p.policy_version,p.model,
                       p.result_mode,p.suggested_role,p.source_evidence
                FROM job_role_ai_proposals p JOIN job_posts j ON j.id=p.job_post_id AND j.version=p.job_version
                WHERE p.review_status='APPROVED' AND p.job_post_id IN (
                """ + placeholders + ") ORDER BY p.reviewed_at DESC,p.id DESC", byId.keySet().toArray());
        Map<Long, String> result = new HashMap<>();
        for (var row : rows) {
            long id = ((Number) row.get("job_post_id")).longValue();
            if (result.containsKey(id)) continue;
            JobPost job = byId.get(id);
            if (job == null || job.getVersion() != ((Number) row.get("job_version")).longValue()
                    || !JobRoleAiRules.VERSION.equals(row.get("policy_version"))) continue;
            try {
                var roles = JobRoleAiCatalog.decodeSnapshot(mapper, row.get("catalog_snapshot"));
                var input = JobRoleAiInput.prepareMatching(id, job.getSourcePlatform().name(), job.getTitle(), job.getJobCategory(), job.getApplyQual(),
                        (String) row.get("catalog_version"), (String) row.get("model"), roles, (String) row.get("input_hash"));
                if (!input.inputHash().equals(row.get("input_hash"))) continue;
                var validated = JobRoleAiRules.validate(mapper.valueToTree(Map.of(
                        "mode", row.get("result_mode"), "role", row.get("suggested_role"),
                        "evidence", row.get("source_evidence"))), input.evidenceSource(), roles);
                if (!"ABSTAIN".equals(validated.mode())) result.put(id, validated.role());
            } catch (IllegalArgumentException | NullPointerException invalid) {
                // Treat missing, malformed or obsolete source/proposal as unavailable.
            }
        }
        return Map.copyOf(result);
    }
}
