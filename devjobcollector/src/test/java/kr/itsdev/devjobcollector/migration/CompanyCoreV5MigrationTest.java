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
class CompanyCoreV5MigrationTest {
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
    void cleanDatabase() {
        flyway(null).clean();
    }

    @AfterAll
    static void cleanAfterTests() {
        if (url != null) {
            flyway(null).clean();
        }
    }

    @Test
    void migratesCleanDatabaseFromV1ToV5() throws SQLException {
        flyway(null).migrate();

        assertThat(scalar("SELECT VERSION()")).startsWith(expectedVersion);
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("5");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"))
                .isEqualTo("20");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('companies', 'company_members')"))
                .isEqualTo("2");
    }

    @Test
    void upgradesV4WithoutChangingExistingRows() throws SQLException {
        flyway("4").migrate();
        insertActiveUser(1, "owner@example.com");
        insertJob(1);
        execute("INSERT INTO job_bookmarks (user_id, job_post_id) VALUES (1, 1)");

        flyway(null).migrate();

        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("5");
        assertThat(scalar("SELECT COUNT(*) FROM users")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM job_bookmarks")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM companies")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM company_members")).isEqualTo("0");
    }

    @Test
    void enforcesCompanyAndMembershipUniquenessAndForeignKeys() throws SQLException {
        flyway(null).migrate();
        insertActiveUser(1, "owner@example.com");
        insertActiveUser(2, "member@example.com");
        insertCompany(1, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        execute("""
                INSERT INTO company_members
                    (company_id, user_id, role, status, joined_at)
                VALUES
                    (1, 1, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP(6))
                """);

        assertThatThrownBy(() -> insertCompany(
                2, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO company_members
                    (company_id, user_id, role, status, joined_at)
                VALUES
                    (1, 1, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP(6))
                """))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO company_members (company_id, user_id, role)
                VALUES (999, 2, 'RECRUITER')
                """))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("DELETE FROM users WHERE id = 1"))
                .isInstanceOf(SQLException.class);

        execute("DELETE FROM companies WHERE id = 1");
        assertThat(scalar("SELECT COUNT(*) FROM company_members")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM users")).isEqualTo("2");
    }

    private void insertActiveUser(long id, String email) throws SQLException {
        execute("""
                INSERT INTO users (id, email, name, status, provider, email_verified_at)
                VALUES (%d, '%s', 'company-user', 'ACTIVE', 'LOCAL', CURRENT_TIMESTAMP(6))
                """.formatted(id, email));
    }

    private void insertJob(long id) throws SQLException {
        execute("""
                INSERT INTO job_posts
                    (id, company_name, end_date, original_sn, original_url, source_platform, start_date, title)
                VALUES
                    (%d, '테스트 기업', CURRENT_DATE + INTERVAL 30 DAY, 'company-v5-job-%d',
                     'https://example.com/jobs/%d', 'SARAMIN', CURRENT_DATE, '백엔드 개발자')
                """.formatted(id, id, id));
    }

    private void insertCompany(long id, String businessNumberHash) throws SQLException {
        execute("""
                INSERT INTO companies
                    (id, legal_name, display_name, business_number_hash,
                     business_number_masked, website_url, created_by)
                VALUES
                    (%d, '테스트 주식회사', '테스트', '%s',
                     '***-**-12345', 'https://example.com', 1)
                """.formatted(id, businessNumberHash));
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
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
