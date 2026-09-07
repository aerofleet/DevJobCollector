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
class CompanyVerificationV6MigrationTest {
    private static String url;
    private static String username;
    private static String password;
    private static String expectedVersion;

    @BeforeAll
    static void configureDataSource() {
        url = System.getenv("DJC_MIGRATION_TEST_URL");
        username = System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root");
        password = System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", "");
        expectedVersion = System.getenv().getOrDefault("DJC_MIGRATION_TEST_EXPECTED_VERSION", "26.7.0");
    }

    @BeforeEach
    void cleanDatabase() { flyway(null).clean(); }

    @AfterAll
    static void cleanAfterTests() {
        if (url != null) { flyway(null).clean(); }
    }

    @Test
    void migratesCleanDatabaseFromV1ToV6() throws SQLException {
        flyway(null).migrate();

        assertThat(scalar("SELECT VERSION()" )).startsWith(expectedVersion);
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("6");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"))
                .isEqualTo("21");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'company_verification_requests'"))
                .isEqualTo("1");
    }

    @Test
    void upgradesV5WithoutChangingCompanyRows() throws SQLException {
        flyway("5").migrate();
        insertUser(1, "owner@example.com", "USER");
        insertCompany();

        flyway(null).migrate();

        assertThat(scalar("SELECT COUNT(*) FROM companies")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM company_verification_requests")).isEqualTo("0");
    }

    @Test
    void enforcesVerificationForeignKeysAndAuditRetention() throws SQLException {
        flyway(null).migrate();
        insertUser(1, "owner@example.com", "USER");
        insertUser(2, "admin@example.com", "PLATFORM_ADMIN");
        insertCompany();
        execute("""
                INSERT INTO company_verification_requests
                    (company_id, requested_by, method, status, evidence_object_key, requested_at)
                VALUES
                    (1, 1, 'BUSINESS_REGISTRATION_DOCUMENT', 'PENDING',
                     'company-verification/1/evidence.pdf', CURRENT_TIMESTAMP(6))
                """);

        assertThatThrownBy(() -> execute("DELETE FROM users WHERE id = 1"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO company_verification_requests
                    (company_id, requested_by, method, evidence_object_key, requested_at)
                VALUES (999, 1, 'BUSINESS_REGISTRATION_DOCUMENT', 'invalid', CURRENT_TIMESTAMP(6))
                """))
                .isInstanceOf(SQLException.class);

        execute("""
                UPDATE company_verification_requests
                SET status = 'APPROVED', reviewed_by = 2, reviewed_at = CURRENT_TIMESTAMP(6)
                WHERE id = 1
                """);
        assertThatThrownBy(() -> execute("DELETE FROM users WHERE id = 2"))
                .isInstanceOf(SQLException.class);
        execute("DELETE FROM companies WHERE id = 1");
        assertThat(scalar("SELECT COUNT(*) FROM company_verification_requests")).isEqualTo("0");
    }

    private void insertUser(long id, String email, String role) throws SQLException {
        execute("""
                INSERT INTO users (id, email, name, role, status, provider, email_verified_at)
                VALUES (%d, '%s', 'verification-user', '%s', 'ACTIVE', 'LOCAL', CURRENT_TIMESTAMP(6))
                """.formatted(id, email, role));
    }

    private void insertCompany() throws SQLException {
        execute("""
                INSERT INTO companies
                    (id, legal_name, display_name, business_number_hash,
                     business_number_masked, created_by)
                VALUES
                    (1, '테스트 주식회사', '테스트', '%s', '***-**-12345', 1)
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
