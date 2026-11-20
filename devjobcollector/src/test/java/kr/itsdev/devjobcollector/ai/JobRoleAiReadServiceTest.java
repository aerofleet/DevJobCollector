package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobRoleAiReadServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final JobPost job = mock(JobPost.class);

    @Test void disabledDisplayDoesNotQueryProposals() {
        assertThat(new JobRoleAiReadService(jdbc, mapper, false).approvedRoles(List.of(job))).isEmpty();
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test void returnsOnlyCurrentValidatedEvidenceBackedApprovedProposal() throws Exception {
        when(job.getId()).thenReturn(13758L);
        when(job.getVersion()).thenReturn(2L);
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.GREENHOUSE);
        when(job.getTitle()).thenReturn("Selection Operations Manager");
        when(job.getApplyQual()).thenReturn("Run selection operations and lead a team.");
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", job.getTitle(), job.getJobCategory(), job.getApplyQual(), "v1", "gpt-5.4-mini");
        Map<String, Object> row = new HashMap<>(Map.of(
                "job_post_id", 13758L, "job_version", 2L, "input_hash", input.inputHash(), "catalog_version", "v1",
                "policy_version", JobRoleAiRules.VERSION, "model", "gpt-5.4-mini", "result_mode", "NEW",
                "suggested_role", "상품 운영 매니저", "source_evidence", "Run selection operations"));
        row.put("catalog_snapshot", mapper.writeValueAsString(input.allowedRoles()));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        var service = new JobRoleAiReadService(jdbc, mapper, true);
        assertThat(service.approvedRoles(List.of(job))).containsEntry(13758L, "상품 운영 매니저");
        row.put("source_evidence", "invented");
        assertThat(service.approvedRoles(List.of(job))).isEmpty();
        row.put("source_evidence", "Run selection operations");
        row.put("job_version", 1L);
        assertThat(service.approvedRoles(List.of(job))).isEmpty();
    }
}
