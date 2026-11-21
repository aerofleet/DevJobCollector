package kr.itsdev.devjobcollector.admin.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.ai.JobFieldAiPolicy;
import kr.itsdev.devjobcollector.ai.JobFieldAiProcessor;
import kr.itsdev.devjobcollector.ai.JobRoleAiInput;
import kr.itsdev.devjobcollector.ai.JobRoleAiRules;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobFieldAiControllerTest {
    private final JobPostRepository jobs = mock(JobPostRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JobFieldAiController controller = new JobFieldAiController(mock(JobFieldAiProcessor.class), jobs, jdbc, new ObjectMapper());
    private final AdminPrincipal admin = new AdminPrincipal(1L, "a@example.com", "admin", AdminRole.ADMIN, Set.of(), "session", "hash");
    private final AdminPrincipal reviewer = new AdminPrincipal(2L, "b@example.com", "reviewer", AdminRole.REVIEWER, Set.of(), "session", "hash");

    @Test void only_admin_can_review_and_source_evidence_is_revalidated() {
        var job = mock(JobPost.class);
        when(job.getId()).thenReturn(1L); when(job.getVersion()).thenReturn(0L);
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.PUBLIC_ALIO);
        when(job.getTitle()).thenReturn("전산 연구원"); when(job.getApplyQual()).thenReturn("시스템 개발과 연구 업무");
        when(jobs.findByIdForModeration(1L)).thenReturn(Optional.of(job));
        var input = JobRoleAiInput.prepare(1, "PUBLIC_ALIO", job.getTitle(), null, job.getApplyQual(),
                JobFieldAiPolicy.VERSION, "gpt-5.4-mini", JobRoleAiRules.EXISTING_ROLES);
        var request = new JobFieldAiController.ReviewRequest(0L, input.inputHash());
        assertThatThrownBy(() -> controller.review(1, 3, "approve", request, reviewer)).hasMessageContaining("ADMIN_ROLE_DENIED");
        var row = new HashMap<String,Object>(Map.of("job_version", 0L, "input_hash", input.inputHash(),
                "policy_version", JobFieldAiPolicy.VERSION, "model", "gpt-5.4-mini", "review_status", "PENDING_REVIEW",
                "fields_json", "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"시스템 개발\"}]}"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertThat(controller.review(1, 3, "approve", request, admin)).containsEntry("status", "APPROVED");
        row.put("fields_json", "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"없는 근거\"}]}");
        assertThatThrownBy(() -> controller.review(1, 3, "approve", request, admin)).hasMessageContaining("FIELD_PROPOSAL_STALE");
    }
}
