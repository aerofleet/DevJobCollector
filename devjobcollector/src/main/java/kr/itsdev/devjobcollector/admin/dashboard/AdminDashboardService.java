package kr.itsdev.devjobcollector.admin.dashboard;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequestRepository;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {
    static final ZoneId OPERATIONS_ZONE = ZoneId.of("Asia/Seoul");

    private final UserAccountRepository userRepository;
    private final CompanyVerificationRequestRepository verificationRepository;
    private final JobPostRepository jobPostRepository;

    public AdminDashboardService(UserAccountRepository userRepository,
                                 CompanyVerificationRequestRepository verificationRepository,
                                 JobPostRepository jobPostRepository) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.jobPostRepository = jobPostRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummary summary() {
        return summaryAt(ZonedDateTime.now(Clock.system(OPERATIONS_ZONE)));
    }

    AdminDashboardSummary summaryAt(ZonedDateTime now) {
        ZonedDateTime operationsNow = now.withZoneSameInstant(OPERATIONS_ZONE);
        LocalDate today = operationsNow.toLocalDate();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate trendStart = today.minusDays(6);

        long totalUsers = userRepository.count();
        long weeklySignups = userRepository
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        weekStart.atStartOfDay(), tomorrowStart);
        long pendingCompanies = verificationRepository
                .countByStatus(CompanyVerificationStatus.PENDING);
        long activeJobs = jobPostRepository
                .countByIsActiveTrueAndModerationStatusAndEndDateGreaterThanEqual(
                        JobModerationStatus.ACTIVE, today);

        Map<LocalDate, Long> trendCounts = new HashMap<>();
        userRepository.countSignupsByDay(trendStart.atStartOfDay(), tomorrowStart)
                .forEach(count -> trendCounts.put(count.getSignupDay(), count.getTotal()));
        var signupTrend = new ArrayList<AdminDashboardSummary.DailySignupPoint>(7);
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = trendStart.plusDays(offset);
            signupTrend.add(new AdminDashboardSummary.DailySignupPoint(
                    date, trendCounts.getOrDefault(date, 0L)));
        }

        Map<String, AdminDashboardSummary.Metric> metrics = Map.of(
                "totalUsers", AdminDashboardSummary.Metric.available(
                        totalUsers, "전체 일반 회원"),
                "weeklySignups", AdminDashboardSummary.Metric.available(
                        weeklySignups, "월요일 00:00부터 현재까지"),
                "pendingCompanies", AdminDashboardSummary.Metric.available(
                        pendingCompanies, "검토 대기 중인 기업 인증 요청"),
                "activeJobs", AdminDashboardSummary.Metric.available(
                        activeJobs, "활성·노출·미마감 공고")
        );
        return new AdminDashboardSummary(
                operationsNow.toOffsetDateTime(), OPERATIONS_ZONE.getId(), metrics, signupTrend,
                new AdminDashboardSummary.ReviewQueue(pendingCompanies));
    }
}
