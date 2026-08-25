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
class CareerHubV4MigrationTest {
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
    void migratesCleanDatabaseFromV1ToV4() throws SQLException {
        flyway(null).migrate();

        assertThat(scalar("SELECT VERSION()" )).startsWith(expectedVersion);
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("4");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"))
                .isEqualTo("18");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('resumes', 'job_bookmarks', 'job_view_history', 'applications')"))
                .isEqualTo("4");
    }

    @Test
    void upgradesV3WithoutCreatingCareerRows() throws SQLException {
        flyway("3").migrate();
        insertActiveUserAndJob();

        flyway(null).migrate();

        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("4");
        assertThat(scalar("SELECT COUNT(*) FROM resumes")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM job_bookmarks")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM job_view_history")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM applications")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM users")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM job_posts")).isEqualTo("1");
    }

    @Test
    void enforcesOwnershipUniquenessAndPositiveViewCount() throws SQLException {
        flyway(null).migrate();
        insertActiveUserAndJob();
        execute("INSERT INTO job_bookmarks (user_id, job_post_id) VALUES (1, 1)");
        execute("INSERT INTO job_view_history (user_id, job_post_id, view_count) VALUES (1, 1, 1)");
        execute("INSERT INTO applications (user_id, job_post_id) VALUES (1, 1)");
        execute("INSERT INTO resumes (user_id, title, content_json) VALUES (1, '기본 이력서', JSON_OBJECT())");

        assertThatThrownBy(() -> execute("INSERT INTO job_bookmarks (user_id, job_post_id) VALUES (1, 1)"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("INSERT INTO job_view_history (user_id, job_post_id, view_count) VALUES (1, 1, 0)"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("INSERT INTO applications (user_id, job_post_id) VALUES (999, 1)"))
                .isInstanceOf(SQLException.class);

        execute("DELETE FROM users WHERE id = 1");
        assertThat(scalar("SELECT COUNT(*) FROM resumes")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM job_bookmarks")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM job_view_history")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM applications")).isEqualTo("0");
    }

    private void insertActiveUserAndJob() throws SQLException {
        execute("""
                INSERT INTO users (id, email, name, status, provider, email_verified_at)
                VALUES (1, 'career@example.com', 'career-user', 'ACTIVE', 'LOCAL', CURRENT_TIMESTAMP(6))
                """);
        execute("""
                INSERT INTO job_posts
                    (id, company_name, end_date, original_sn, original_url, source_platform, start_date, title)
                VALUES
                    (1, '테스트 기업', CURRENT_DATE + INTERVAL 30 DAY, 'career-v4-job',
                     'https://example.com/jobs/1', 'SARAMIN', CURRENT_DATE, '백엔드 개발자')
                """);
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
