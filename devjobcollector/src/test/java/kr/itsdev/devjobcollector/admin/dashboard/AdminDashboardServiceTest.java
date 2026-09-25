package kr.itsdev.devjobcollector.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequestRepository;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import org.junit.jupiter.api.Test;

class AdminDashboardServiceTest {
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private final CompanyVerificationRequestRepository verificationRepository =
            mock(CompanyVerificationRequestRepository.class);
    private final JobPostRepository jobPostRepository = mock(JobPostRepository.class);
    private final AdminDashboardService service = new AdminDashboardService(
            userRepository, verificationRepository, jobPostRepository);

    @Test
    void summarizesOperationsMetricsAndZeroFillsSevenDayTrend() {
        ZonedDateTime now = ZonedDateTime.of(
                2026, 9, 25, 9, 30, 0, 0, ZoneId.of("Asia/Seoul"));
        when(userRepository.count()).thenReturn(1_280L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 9, 21, 0, 0),
                LocalDateTime.of(2026, 9, 26, 0, 0))).thenReturn(42L);
        when(verificationRepository.countByStatus(CompanyVerificationStatus.PENDING))
                .thenReturn(7L);
        when(jobPostRepository.countByIsActiveTrueAndModerationStatusAndEndDateGreaterThanEqual(
                JobModerationStatus.ACTIVE, LocalDate.of(2026, 9, 25))).thenReturn(314L);
        UserAccountRepository.DailySignupCount firstDay =
                dailyCount(LocalDate.of(2026, 9, 19), 3L);
        UserAccountRepository.DailySignupCount lastDay =
                dailyCount(LocalDate.of(2026, 9, 25), 9L);
        when(userRepository.countSignupsByDay(
                LocalDateTime.of(2026, 9, 19, 0, 0),
                LocalDateTime.of(2026, 9, 26, 0, 0)))
                .thenReturn(List.of(firstDay, lastDay));

        AdminDashboardSummary result = service.summaryAt(now);

        assertThat(result.timezone()).isEqualTo("Asia/Seoul");
        assertThat(result.asOf().getOffset().getTotalSeconds()).isEqualTo(9 * 60 * 60);
        assertThat(result.metrics()).extractingByKey("totalUsers")
                .extracting(AdminDashboardSummary.Metric::value).isEqualTo(1_280L);
        assertThat(result.metrics()).extractingByKey("weeklySignups")
                .extracting(AdminDashboardSummary.Metric::value).isEqualTo(42L);
        assertThat(result.metrics()).extractingByKey("pendingCompanies")
                .extracting(AdminDashboardSummary.Metric::value).isEqualTo(7L);
        assertThat(result.metrics()).extractingByKey("activeJobs")
                .extracting(AdminDashboardSummary.Metric::value).isEqualTo(314L);
        assertThat(result.signupTrend()).hasSize(7)
                .extracting(AdminDashboardSummary.DailySignupPoint::value)
                .containsExactly(3L, 0L, 0L, 0L, 0L, 0L, 9L);
        assertThat(result.reviewQueue().pendingCompanyVerifications()).isEqualTo(7L);

        verify(verificationRepository).countByStatus(CompanyVerificationStatus.PENDING);
        verify(jobPostRepository)
                .countByIsActiveTrueAndModerationStatusAndEndDateGreaterThanEqual(
                        JobModerationStatus.ACTIVE, LocalDate.of(2026, 9, 25));
    }

    private UserAccountRepository.DailySignupCount dailyCount(LocalDate date, long total) {
        UserAccountRepository.DailySignupCount count =
                mock(UserAccountRepository.DailySignupCount.class);
        when(count.getSignupDay()).thenReturn(date);
        when(count.getTotal()).thenReturn(total);
        return count;
    }
}
