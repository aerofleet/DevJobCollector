package kr.itsdev.devjobcollector.admin.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminJobServiceTest {
    private final JobPostRepository jobs = mock(JobPostRepository.class);
    private final JobModerationHistoryRepository history = mock(JobModerationHistoryRepository.class);
    private final AdminAuditLogRepository audit = mock(AdminAuditLogRepository.class);
    private final AdminJobService service = new AdminJobService(jobs, history, audit);
    private final AdminPrincipal admin = new AdminPrincipal(4L, "admin@example.com", "Admin",
            AdminRole.ADMIN, Set.of(), "session", "csrf");

    @Test
    void hidesAndRecordsHistoryAndAudit() {
        JobPost job = job();
        when(jobs.findByIdForModeration(7L)).thenReturn(Optional.of(job));

        var result = service.moderate(7L, JobModerationStatus.HIDDEN, 0,
                "policy", admin, "request", "127.0.0.1", "test");

        assertThat(result.moderationStatus()).isEqualTo("HIDDEN");
        verify(history).save(any(JobModerationHistory.class));
        verify(audit).save(any(AdminAuditLog.class));
    }

    @Test
    void rejectsStaleVersionWithoutAudit() {
        when(jobs.findByIdForModeration(7L)).thenReturn(Optional.of(job()));

        assertThatThrownBy(() -> service.moderate(7L, JobModerationStatus.HIDDEN, 1,
                "policy", admin, "request", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JOB_VERSION_CONFLICT");
        verify(history, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void closedJobCannotBeRestored() {
        JobPost job = job();
        job.changeModerationStatus(JobModerationStatus.CLOSED);
        when(jobs.findByIdForModeration(7L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.moderate(7L, JobModerationStatus.ACTIVE, 0,
                "restore", admin, "request", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JOB_STATUS_CONFLICT");
    }

    @Test
    void reviewerCannotChangeJob() {
        AdminPrincipal reviewer = new AdminPrincipal(5L, "reviewer@example.com", "Reviewer",
                AdminRole.REVIEWER, Set.of(), "session", "csrf");
        assertThatThrownBy(() -> service.moderate(7L, JobModerationStatus.HIDDEN, 0,
                "policy", reviewer, "request", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ADMIN_ROLE_DENIED");
        verify(jobs, never()).findByIdForModeration(any());
    }

    private JobPost job() {
        return JobPost.builder().sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn("admin-job").companyName("Company").title("Backend")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7))
                .originalUrl("https://example.com/jobs/1").build();
    }
}
