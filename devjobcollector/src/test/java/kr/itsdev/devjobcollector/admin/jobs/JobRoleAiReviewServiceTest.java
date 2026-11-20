package kr.itsdev.devjobcollector.admin.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.ai.JobRoleAiInput;
import kr.itsdev.devjobcollector.ai.JobRoleAiProcessor;
import kr.itsdev.devjobcollector.ai.JobRoleAiRules;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobRoleAiReviewServiceTest {
    private final JobPostRepository jobs = mock(JobPostRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JobRoleAiReviewService service = new JobRoleAiReviewService(jobs, jdbc, new ObjectMapper(), mock(JobRoleAiProcessor.class));
    private final AdminPrincipal admin = new AdminPrincipal(1L, "admin@example.com", "admin", AdminRole.ADMIN, Set.of(), "session", "hash");
    private final AdminPrincipal reviewer = new AdminPrincipal(2L, "review@example.com", "reviewer", AdminRole.REVIEWER, Set.of(), "session", "hash");

    @Test void requiresAnAdministratorAndRejectsChangedJobsBeforeReview() {
        assertThatThrownBy(() -> service.approve(13758, 4, 0, "a".repeat(64), reviewer)).hasMessageContaining("ADMIN_ROLE_DENIED");
        JobPost job = mock(JobPost.class);
        when(job.getVersion()).thenReturn(1L);
        when(jobs.findByIdForModeration(13758L)).thenReturn(Optional.of(job));
        assertThatThrownBy(() -> service.approve(13758, 4, 0, "a".repeat(64), admin)).hasMessageContaining("JOB_CHANGED");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void approvalRevalidatesSourceAndCannotApproveInventedEvidence() throws Exception {
        JobPost job = mock(JobPost.class);
        when(job.getVersion()).thenReturn(2L);
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.GREENHOUSE);
        when(job.getTitle()).thenReturn("Selection Operations Manager");
        when(job.getApplyQual()).thenReturn("Run selection operations and lead a team.");
        when(jobs.findByIdForModeration(13758L)).thenReturn(Optional.of(job));
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", job.getTitle(), job.getJobCategory(), job.getApplyQual(), "v1", "gpt-5.4-mini");
        Map<String, Object> row = new HashMap<>(Map.of("job_version", 2L, "input_hash", input.inputHash(), "catalog_version", "v1",
                "policy_version", JobRoleAiRules.VERSION, "model", "gpt-5.4-mini", "result_mode", "NEW", "suggested_role", "상품 운영 매니저",
                "source_evidence", "Run selection operations", "review_status", "PENDING_REVIEW"));
        row.put("catalog_snapshot", new ObjectMapper().writeValueAsString(input.allowedRoles()));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertThat(service.approve(13758, 4, 2, input.inputHash(), admin)).containsEntry("status", "APPROVED");
        row.put("source_evidence", "invented evidence");
        assertThatThrownBy(() -> service.approve(13758, 4, 2, input.inputHash(), admin)).hasMessageContaining("ROLE_PROPOSAL_STALE");
    }
}
