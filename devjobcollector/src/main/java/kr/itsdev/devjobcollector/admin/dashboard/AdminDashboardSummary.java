package kr.itsdev.devjobcollector.admin.dashboard;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AdminDashboardSummary(
        OffsetDateTime asOf,
        String timezone,
        Map<String, Metric> metrics,
        List<DailySignupPoint> signupTrend,
        ReviewQueue reviewQueue
) {
    public AdminDashboardSummary {
        metrics = Map.copyOf(metrics);
        signupTrend = List.copyOf(signupTrend);
    }

    public record Metric(long value, boolean dataAvailable, String description) {
        public static Metric available(long value, String description) {
            return new Metric(value, true, description);
        }
    }

    public record DailySignupPoint(LocalDate date, long value) {}

    public record ReviewQueue(long pendingCompanyVerifications) {}
}
