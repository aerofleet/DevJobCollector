package kr.itsdev.devjobcollector.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
class SecurityAuditV7MigrationTest {
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
    void migratesCleanDatabaseThroughV7() throws SQLException {
        flyway(null).migrate();

        assertThat(scalar("SELECT VERSION()" )).startsWith(expectedVersion);
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 "
                + "ORDER BY installed_rank DESC LIMIT 1")).isEqualTo("7");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE()")).isEqualTo("22");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'security_audit_events'"))
                .isEqualTo("1");
    }

    @Test
    void upgradesV6WithoutChangingExistingCompanyData() throws SQLException {
        flyway("6").migrate();
        insertUserAndCompany();

        flyway(null).migrate();

        assertThat(scalar("SELECT COUNT(*) FROM companies")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM security_audit_events")).isEqualTo("0");
    }

    @Test
    void retainsIdOnlyAuditTrailAfterSourceRowsAreRemoved() throws SQLException {
        flyway(null).migrate();
        insertUserAndCompany();
        execute("""
                INSERT INTO security_audit_events
                    (event_type, actor_user_id, subject_user_id, company_id,
                     previous_value, new_value, occurred_at)
                VALUES
                    ('COMPANY_MEMBER_ROLE_CHANGED', 1, 1, 1,
                     'VIEWER', 'RECRUITER', CURRENT_TIMESTAMP(6))
                """);

        execute("DELETE FROM companies WHERE id = 1");
        execute("DELETE FROM users WHERE id = 1");

        assertThat(scalar("SELECT COUNT(*) FROM security_audit_events")).isEqualTo("1");
        assertThat(scalar("SELECT actor_user_id FROM security_audit_events WHERE id = 1"))
                .isEqualTo("1");
        assertThat(scalar("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'security_audit_events'
                  AND column_name IN ('email', 'business_number', 'business_number_hash',
                                      'evidence_object_key', 'token', 'secret')
                """)).isEqualTo("0");
    }

    private void insertUserAndCompany() throws SQLException {
        execute("""
                INSERT INTO users (id, email, name, role, status, provider, email_verified_at)
                VALUES (1, 'audit@example.com', 'audit-user', 'USER', 'ACTIVE', 'LOCAL',
                        CURRENT_TIMESTAMP(6))
                """);
        execute("""
                INSERT INTO companies
                    (id, legal_name, display_name, business_number_hash,
                     business_number_masked, created_by)
                VALUES
                    (1, '감사 주식회사', '감사', '%s', '***-**-12345', 1)
                """.formatted("a".repeat(64)));
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
