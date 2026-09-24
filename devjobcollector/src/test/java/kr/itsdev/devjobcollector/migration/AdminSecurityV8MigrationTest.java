package kr.itsdev.devjobcollector.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
class AdminSecurityV8MigrationTest {
    private static String url;
    private static String username;
    private static String password;
    private static String expectedVersion;

    @BeforeAll
    static void configureDataSource() {
        url = System.getenv("DJC_MIGRATION_TEST_URL");
        username = System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root");
        password = System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", "");
        expectedVersion = System.getenv().getOrDefault(
                "DJC_MIGRATION_TEST_EXPECTED_VERSION", "26.7.0");
    }

    @BeforeEach
    void cleanDatabase() { flyway(null).clean(); }

    @AfterAll
    static void cleanAfterTests() {
        if (url != null) { flyway(null).clean(); }
    }

    @Test
    void migratesCleanDatabaseThroughV8() throws SQLException {
        flyway(null).migrate();

        assertThat(scalar("SELECT VERSION()" )).startsWith(expectedVersion);
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 "
                + "ORDER BY installed_rank DESC LIMIT 1")).isEqualTo("8");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE()")).isEqualTo("30");
        assertThat(scalar("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('admin_accounts', 'admin_account_permissions',
                    'admin_sessions', 'admin_audit_logs', 'admin_idempotency_keys',
                    'user_moderation_history', 'company_verification_history',
                    'job_moderation_history')
                """)).isEqualTo("8");
        assertThat(scalar("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND ((table_name = 'users' AND column_name = 'version')
                    OR (table_name = 'companies' AND column_name = 'version')
                    OR (table_name = 'job_posts' AND column_name IN ('version', 'moderation_status')))
                """)).isEqualTo("4");
    }

    @Test
    void upgradesV7AndBackfillsConcurrencyAndModerationColumns() throws SQLException {
        flyway("7").migrate();
        insertUserCompanyAndJob();

        flyway(null).migrate();

        assertThat(scalar("SELECT version FROM users WHERE id = 1")).isEqualTo("0");
        assertThat(scalar("SELECT version FROM companies WHERE id = 1")).isEqualTo("0");
        assertThat(scalar("SELECT version FROM job_posts WHERE id = 1")).isEqualTo("0");
        assertThat(scalar("SELECT moderation_status FROM job_posts WHERE id = 1"))
                .isEqualTo("ACTIVE");
        assertThat(scalar("SELECT COUNT(*) FROM users")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM companies")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM job_posts")).isEqualTo("1");
    }

    @Test
    void enforcesCaseInsensitiveAdminEmailAndRefreshHashUniqueness() throws SQLException {
        flyway(null).migrate();
        insertAdmin(1, "admin@example.com");

        assertThatThrownBy(() -> insertAdmin(2, "ADMIN@example.com"))
                .isInstanceOf(SQLException.class);

        execute("""
                INSERT INTO admin_sessions
                    (id, admin_id, refresh_hash, csrf_hash, issued_at, expires_at)
                VALUES
                    ('11111111-1111-1111-1111-111111111111', 1, '%s', '%s',
                     CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR))
                """.formatted("a".repeat(64), "b".repeat(64)));

        assertThatThrownBy(() -> execute("""
                INSERT INTO admin_sessions
                    (id, admin_id, refresh_hash, csrf_hash, issued_at, expires_at)
                VALUES
                    ('22222222-2222-2222-2222-222222222222', 1, '%s', '%s',
                     CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR))
                """.formatted("a".repeat(64), "c".repeat(64))))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void keepsAuditLogAfterAdminDeletionWithoutPlaintextSecretColumns() throws SQLException {
        flyway(null).migrate();
        insertAdmin(1, "audit-admin@example.com");
        execute("""
                INSERT INTO admin_audit_logs
                    (actor_admin_id, action, target_type, target_id, reason,
                     before_json, after_json, result, request_id)
                VALUES
                    (1, 'USER_SUSPEND', 'USER', '42', 'policy violation',
                     JSON_OBJECT('status', 'ACTIVE'), JSON_OBJECT('status', 'SUSPENDED'),
                     'SUCCESS', 'request-1')
                """);

        execute("DELETE FROM admin_accounts WHERE id = 1");

        assertThat(scalar("SELECT COUNT(*) FROM admin_audit_logs")).isEqualTo("1");
        assertThat(scalar("SELECT actor_admin_id FROM admin_audit_logs WHERE id = 1"))
                .isEqualTo("1");
        assertThat(scalar("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN ('admin_accounts', 'admin_sessions', 'admin_audit_logs')
                  AND column_name IN ('password', 'mfa_secret', 'refresh_token',
                                      'csrf_token', 'access_token')
                """)).isEqualTo("0");
    }

    private void insertUserCompanyAndJob() throws SQLException {
        execute("""
                INSERT INTO users (id, email, name, role, status, provider, email_verified_at)
                VALUES (1, 'admin-foundation@example.com', 'user', 'USER', 'ACTIVE', 'LOCAL',
                        CURRENT_TIMESTAMP(6))
                """);
        execute("""
                INSERT INTO companies
                    (id, legal_name, display_name, business_number_hash,
                     business_number_masked, created_by)
                VALUES (1, '관리자 기반 주식회사', '관리자 기반', '%s', '***-**-12345', 1)
                """.formatted("d".repeat(64)));
        execute("""
                INSERT INTO job_posts
                    (id, company_name, end_date, is_active, original_sn, original_url,
                     source_platform, start_date, title)
                VALUES
                    (1, '관리자 기반', '2026-12-31', 1, 'admin-v8-job',
                     'https://example.com/jobs/1', 'SARAMIN', '2026-09-24', '백엔드 개발자')
                """);
    }

    private void insertAdmin(long id, String email) throws SQLException {
        execute("""
                INSERT INTO admin_accounts (id, email, password_hash, name, role, status)
                VALUES (%d, '%s', '%s', '관리자', 'ADMIN', 'ACTIVE')
                """.formatted(id, email, "p".repeat(60)));
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) { configuration.target(target); }
        return configuration.load();
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String scalar(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
