package kr.itsdev.devjobcollector.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
class CareerHubV4AuditTest {

    private static final Path AUDIT_SQL = Path.of("ops", "db", "audit-career-v4.sql");
    private static String url;
    private static String username;
    private static String password;

    @BeforeAll
    static void configureDataSource() {
        url = System.getenv("DJC_MIGRATION_TEST_URL");
        username = System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root");
        password = System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", "");
    }

    @BeforeEach
    void prepareV4Fixture() throws SQLException {
        flyway().clean();
        flyway().migrate();
        execute("""
                INSERT INTO users (email, name, status, provider, provider_user_id, email_verified_at)
                VALUES
                    ('career-owner@example.com', 'career-owner', 'ACTIVE', 'GITHUB', 'career-owner-subject', CURRENT_TIMESTAMP(6)),
                    ('career-other@example.com', 'career-other', 'ACTIVE', 'GITHUB', 'career-other-subject', CURRENT_TIMESTAMP(6))
                """);
        execute("""
                INSERT INTO job_posts
                    (company_name, end_date, original_sn, original_url, source_platform, start_date, title)
                VALUES
                    ('career-company', '2026-09-25', 'career-job-1', 'https://example.com/jobs/1', 'SARAMIN', '2026-08-25', 'career-job-1'),
                    ('career-company', '2026-09-26', 'career-job-2', 'https://example.com/jobs/2', 'SARAMIN', '2026-08-25', 'career-job-2')
                """);
        execute("""
                INSERT INTO resumes (user_id, title, resume_status, content_json)
                SELECT id, 'backend resume', 'DRAFT', JSON_OBJECT('summary', 'backend')
                FROM users WHERE email = 'career-owner@example.com'
                """);
        execute("""
                INSERT INTO job_bookmarks (user_id, job_post_id)
                SELECT user_account.id, job_post.id
                FROM users user_account, job_posts job_post
                WHERE user_account.email = 'career-owner@example.com'
                  AND job_post.original_sn = 'career-job-1'
                """);
        execute("""
                INSERT INTO job_view_history
                    (user_id, job_post_id, first_viewed_at, last_viewed_at, view_count)
                SELECT user_account.id, job_post.id, '2026-08-25 10:00:00', '2026-08-25 10:05:00', 2
                FROM users user_account, job_posts job_post
                WHERE user_account.email = 'career-owner@example.com'
                  AND job_post.original_sn = 'career-job-1'
                """);
        execute("""
                INSERT INTO applications (user_id, job_post_id, application_status, applied_at)
                SELECT user_account.id, job_post.id, 'APPLIED', '2026-08-25 10:10:00'
                FROM users user_account, job_posts job_post
                WHERE user_account.email = 'career-owner@example.com'
                  AND job_post.original_sn = 'career-job-1'
                """);
    }

    @AfterAll
    static void cleanAfterTests() {
        if (url != null) {
            flyway().clean();
        }
    }

    @Test
    void reportsZeroDefectsForValidCareerData() throws Exception {
        Map<String, Long> audit = audit();

        assertThat(audit)
                .containsEntry("resume_total", 1L)
                .containsEntry("bookmark_total", 1L)
                .containsEntry("view_history_total", 1L)
                .containsEntry("application_total", 1L);
        assertThat(audit.entrySet())
                .filteredOn(entry -> !entry.getKey().endsWith("_total"))
                .allMatch(entry -> entry.getValue() == 0L);
    }

    @Test
    void detectsOwnerAndJobOrphansAfterConstraintDrift() throws Exception {
        executeWithoutForeignKeyChecks(
                "INSERT INTO resumes (user_id, title, content_json) VALUES (900001, 'orphan', JSON_OBJECT())",
                "INSERT INTO job_bookmarks (user_id, job_post_id) VALUES (900002, 900101)",
                "INSERT INTO job_view_history (user_id, job_post_id) VALUES (900003, 900102)",
                "INSERT INTO applications (user_id, job_post_id) VALUES (900004, 900103)"
        );

        assertThat(audit())
                .containsEntry("resume_user_orphan_count", 1L)
                .containsEntry("bookmark_user_orphan_count", 1L)
                .containsEntry("view_history_user_orphan_count", 1L)
                .containsEntry("application_user_orphan_count", 1L)
                .containsEntry("bookmark_job_orphan_count", 1L)
                .containsEntry("view_history_job_orphan_count", 1L)
                .containsEntry("application_job_orphan_count", 1L);
    }

    @Test
    void detectsDuplicateOwnerJobPairsAfterUniqueIndexDrift() throws Exception {
        execute("ALTER TABLE job_bookmarks DROP INDEX uk_job_bookmarks_user_job");
        execute("ALTER TABLE job_view_history DROP INDEX uk_job_view_history_user_job");
        execute("ALTER TABLE applications DROP INDEX uk_applications_user_job");
        execute("INSERT INTO job_bookmarks (user_id, job_post_id) SELECT user_id, job_post_id FROM job_bookmarks LIMIT 1");
        execute("INSERT INTO job_view_history (user_id, job_post_id) SELECT user_id, job_post_id FROM job_view_history LIMIT 1");
        execute("INSERT INTO applications (user_id, job_post_id) SELECT user_id, job_post_id FROM applications LIMIT 1");

        assertThat(audit())
                .containsEntry("bookmark_owner_job_duplicate_count", 1L)
                .containsEntry("view_history_owner_job_duplicate_count", 1L)
                .containsEntry("application_owner_job_duplicate_count", 1L);
    }

    @Test
    void detectsInvalidStateAndHistoryValuesAfterConstraintDrift() throws Exception {
        execute("ALTER TABLE job_view_history DROP CHECK chk_job_view_history_view_count");
        execute("UPDATE resumes SET title = '', resume_status = 'UNKNOWN'");
        execute("UPDATE job_view_history SET view_count = 0, last_viewed_at = first_viewed_at - INTERVAL 1 SECOND");
        execute("UPDATE applications SET application_status = 'UNKNOWN'");

        assertThat(audit())
                .containsEntry("resume_invalid_status_count", 1L)
                .containsEntry("resume_blank_title_count", 1L)
                .containsEntry("view_history_invalid_count_count", 1L)
                .containsEntry("view_history_invalid_chronology_count", 1L)
                .containsEntry("application_invalid_status_count", 1L);
    }

    @Test
    void auditContractIsOneReadOnlyAggregateQueryWithoutSensitiveColumns() throws Exception {
        String sql = Files.readString(AUDIT_SQL);
        String executableSql = sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (left, right) -> left + "\n" + right)
                .strip();

        assertThat(executableSql).startsWith("SELECT ").endsWith(";");
        assertThat(executableSql.substring(0, executableSql.length() - 1)).doesNotContain(";");
        assertThat(executableSql.toUpperCase()).doesNotContain("INSERT ", "UPDATE ", "DELETE ", "ALTER ", "DROP ");
        assertThat(executableSql.toLowerCase()).doesNotContain("email", "provider_subject", "password_hash", "memo");
    }

    private static Map<String, Long> audit() throws SQLException, IOException {
        String sql = Files.readString(AUDIT_SQL);
        Map<String, Long> metrics = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                metrics.put(resultSet.getString("metric"), resultSet.getLong("value"));
            }
        }
        return metrics;
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeWithoutForeignKeyChecks(String... statements) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }
}
