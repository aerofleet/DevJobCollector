package kr.itsdev.devjobcollector.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, AdminDashboardService.class})
class AdminDashboardIntegrationTest {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminDashboardService dashboardService;

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @Test
    void aggregatesDashboardWithinP95Target() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        AdminDashboardSummary baseline = dashboardService.summary();
        long requesterId = insertUser("dashboard-current@example.com", today.atTime(8, 30));
        insertUser("dashboard-old@example.com", today.minusDays(10).atTime(8, 30));
        long companyId = insertCompany(requesterId);
        insertVerificationRequest(companyId, requesterId, today.atTime(9, 0));
        insertJob("dashboard-active", true, "ACTIVE", today.plusDays(7));
        insertJob("dashboard-hidden", true, "HIDDEN", today.plusDays(7));
        insertJob("dashboard-expired", true, "ACTIVE", today.minusDays(1));

        AdminDashboardSummary result = dashboardService.summary();

        assertThat(result.metrics().get("totalUsers").value())
                .isEqualTo(baseline.metrics().get("totalUsers").value() + 2L);
        assertThat(result.metrics().get("weeklySignups").value())
                .isEqualTo(baseline.metrics().get("weeklySignups").value() + 1L);
        assertThat(result.metrics().get("pendingCompanies").value())
                .isEqualTo(baseline.metrics().get("pendingCompanies").value() + 1L);
        assertThat(result.metrics().get("activeJobs").value())
                .isEqualTo(baseline.metrics().get("activeJobs").value() + 1L);
        assertThat(result.signupTrend()).hasSize(7);
        assertThat(result.signupTrend().getLast().value())
                .isEqualTo(baseline.signupTrend().getLast().value() + 1L);

        for (int warmup = 0; warmup < 3; warmup++) dashboardService.summary();
        var durations = new ArrayList<Long>(30);
        for (int sample = 0; sample < 30; sample++) {
            long started = System.nanoTime();
            dashboardService.summary();
            durations.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(durations);
        long p95Millis = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        System.out.printf("ADMIN_DASHBOARD_SUMMARY_P95_MS=%d%n", p95Millis);

        assertThat(p95Millis).as("dashboard summary p95 milliseconds").isLessThan(800L);
    }

    private long insertUser(String email, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO users
                    (email, name, role, status, provider, created_at, updated_at, version)
                VALUES (?, '대시보드 회원', 'USER', 'ACTIVE', 'LOCAL', ?, ?, 0)
                """, email, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertCompany(long requesterId) {
        jdbcTemplate.update("""
                INSERT INTO companies
                    (legal_name, display_name, business_number_hash, business_number_masked,
                     status, created_by, version)
                VALUES ('대시보드 법인', '대시보드 기업', ?, '123-**-*****',
                        'PENDING_VERIFICATION', ?, 0)
                """, "d".repeat(64), requesterId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM companies WHERE business_number_hash = ?",
                Long.class, "d".repeat(64));
    }

    private void insertVerificationRequest(long companyId, long requesterId,
                                           LocalDateTime requestedAt) {
        jdbcTemplate.update("""
                INSERT INTO company_verification_requests
                    (company_id, requested_by, method, status, evidence_object_key, requested_at)
                VALUES (?, ?, 'BUSINESS_REGISTRATION_DOCUMENT', 'PENDING',
                        'admin-dashboard/evidence', ?)
                """, companyId, requesterId, Timestamp.valueOf(requestedAt));
    }

    private void insertJob(String originalSn, boolean active, String moderationStatus,
                           LocalDate endDate) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbcTemplate.update("""
                INSERT INTO job_posts
                    (company_name, end_date, is_active, original_sn, original_url,
                     source_platform, start_date, title, moderation_status, version, created_at)
                VALUES ('대시보드 기업', ?, ?, ?, 'https://example.com/jobs/dashboard',
                        'GREENHOUSE', ?, '대시보드 공고', ?, 0, ?)
                """, Date.valueOf(endDate), active, originalSn, Date.valueOf(today.minusDays(1)),
                moderationStatus, Timestamp.valueOf(today.atTime(7, 0)));
    }
}
