package kr.itsdev.devjobcollector.admin.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
@Import({QuerydslConfig.class, AdminJobService.class})
class AdminJobIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired JobPostRepository jobs;
    @Autowired AdminJobService service;

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
    void hidesRestoresAndClosesWithoutLeakingIntoPublicSearch() {
        jdbc.update("""
                INSERT INTO admin_accounts (id, email, password_hash, name, role, status)
                VALUES (910, 'job-moderator@example.com', ?, 'Moderator', 'ADMIN', 'ACTIVE')
                """, "p".repeat(60));
        JobPost job = jobs.saveAndFlush(JobPost.builder().sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn("admin-visibility-job").companyName("Company")
                .title("Backend visibility job").startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .originalUrl("https://example.com/jobs/admin-visibility").build());
        Long id = job.getId();
        AdminPrincipal actor = new AdminPrincipal(910L, "job-moderator@example.com", "Moderator",
                AdminRole.ADMIN, Set.of(), "session", "csrf");
        assertThat(publicSearch()).extracting(JobPost::getId).contains(id);

        var hidden = service.moderate(id, JobModerationStatus.HIDDEN, job.getVersion(),
                "policy", actor, "job-request-1", "127.0.0.1", "test");
        entityManager.clear();
        assertThat(publicSearch()).extracting(JobPost::getId).doesNotContain(id);
        assertThat(jobs.findActiveAndNotExpiredSlice(LocalDate.now(), PageRequest.of(0, 20))
                .getContent()).extracting(JobPost::getId).doesNotContain(id);
        assertThat(service.list("Backend visibility", JobModerationStatus.HIDDEN, 0, 20)
                .getContent()).extracting(AdminJobService.JobView::id).contains(id);

        var restored = service.moderate(id, JobModerationStatus.ACTIVE, hidden.version(),
                "appeal", actor, "job-request-2", "127.0.0.1", "test");
        entityManager.clear();
        assertThat(publicSearch()).extracting(JobPost::getId).contains(id);
        assertThat(jobs.findActiveAndNotExpiredSlice(LocalDate.now(), PageRequest.of(0, 20))
                .getContent()).extracting(JobPost::getId).contains(id);

        var closed = service.moderate(id, JobModerationStatus.CLOSED, restored.version(),
                "closed", actor, "job-request-3", "127.0.0.1", "test");
        entityManager.clear();
        assertThat(publicSearch()).extracting(JobPost::getId).doesNotContain(id);
        var reopened = service.moderate(id, JobModerationStatus.ACTIVE, closed.version(),
                "reposted", actor, "job-request-4", "127.0.0.1", "test");
        entityManager.clear();
        assertThat(reopened.moderationStatus()).isEqualTo("ACTIVE");
        assertThat(publicSearch()).extracting(JobPost::getId).contains(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_moderation_history WHERE job_id = ?",
                Long.class, id)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs "
                + "WHERE target_type = 'JOB' AND target_id = ?",
                Long.class, id.toString())).isEqualTo(4L);

        for (int i = 0; i < 3; i++) service.list("Backend visibility", null, 0, 20);
        var durations = new ArrayList<Long>();
        for (int i = 0; i < 30; i++) {
            long started = System.nanoTime();
            service.list("Backend visibility", null, 0, 20);
            durations.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(durations);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        System.out.printf("ADMIN_JOB_LIST_P95_MS=%d%n", p95);
        assertThat(p95).isLessThanOrEqualTo(500L);
    }

    private java.util.List<JobPost> publicSearch() {
        return jobs.searchByAllFieldsOptimized("Backend visibility", null, null, null, null,
                LocalDate.now(), PageRequest.of(0, 20)).getContent();
    }
}
