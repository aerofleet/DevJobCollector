package kr.itsdev.devjobcollector.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.monitoring.JobSearchMetrics;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class JobPostModerationVisibilityTest {
    @Test
    void hiddenJobDoesNotHavePublicDetail() {
        JobPostRepository jobs = mock(JobPostRepository.class);
        JobPost job = JobPost.builder().sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn("hidden-job").companyName("Company").title("Backend")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7))
                .originalUrl("https://example.com/jobs/1").build();
        job.changeModerationStatus(JobModerationStatus.HIDDEN);
        when(jobs.findById(7L)).thenReturn(Optional.of(job));
        JobPostService service = new JobPostService(jobs, mock(JobSearchMetrics.class));

        assertThatThrownBy(() -> service.getJobPostDetail(7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JOB_NOT_AVAILABLE");
    }

    @Test
    void inactiveOrExpiredJobDoesNotHavePublicDetail() {
        JobPostRepository jobs = mock(JobPostRepository.class);
        JobPost inactive = JobPost.builder().sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn("inactive-job").companyName("Company").title("Backend")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7))
                .originalUrl("https://example.com/jobs/2").build();
        inactive.deactivate();
        JobPost expired = JobPost.builder().sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn("expired-job").companyName("Company").title("Backend")
                .startDate(LocalDate.now().minusDays(7)).endDate(LocalDate.now().minusDays(1))
                .originalUrl("https://example.com/jobs/3").build();
        when(jobs.findById(8L)).thenReturn(Optional.of(inactive));
        when(jobs.findById(9L)).thenReturn(Optional.of(expired));
        JobPostService service = new JobPostService(jobs, mock(JobSearchMetrics.class));

        assertThatThrownBy(() -> service.getJobPostDetail(8L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("JOB_NOT_AVAILABLE");
        assertThatThrownBy(() -> service.getJobPostDetail(9L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("JOB_NOT_AVAILABLE");
    }
}
