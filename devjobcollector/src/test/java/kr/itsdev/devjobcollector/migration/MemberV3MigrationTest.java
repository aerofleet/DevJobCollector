package kr.itsdev.devjobcollector.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
class MemberV3MigrationTest {

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
    void migratesCleanDatabaseFromV1ToV3() throws SQLException {
        flyway(null).migrate();

        assertThat(scalar("SELECT VERSION()")).startsWith(expectedVersion);
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("3");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"))
                .isEqualTo("14");
        assertThat(scalar("SELECT COUNT(*) FROM user_identities")).isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM user_consents")).isEqualTo("0");
    }

    @Test
    void upgradesV2UsersWithoutInventingLegacyConsents() throws SQLException {
        migrateToV2();
        execute("""
                INSERT INTO users
                    (email, name, password_hash, status, provider, provider_user_id, email_verified_at)
                VALUES
                    (' Active.Local@Example.com ', 'active-local', 'hash-1', 'ACTIVE', 'LOCAL', NULL, CURRENT_TIMESTAMP(6)),
                    ('pending@example.com', 'pending-local', 'hash-2', 'PENDING_EMAIL', 'LOCAL', NULL, NULL),
                    ('google@example.com', 'google-user', NULL, 'ACTIVE', 'GOOGLE', 'Google-Subject', CURRENT_TIMESTAMP(6)),
                    ('github@example.com', 'github-user', NULL, 'ACTIVE', 'GITHUB', 'GitHub-Subject', CURRENT_TIMESTAMP(6))
                """);

        flyway(null).migrate();

        assertThat(scalar("SELECT COUNT(*) FROM user_identities")).isEqualTo("4");
        assertThat(scalar("SELECT COUNT(*) FROM personal_profiles")).isEqualTo("3");
        assertThat(scalar("SELECT COUNT(*) FROM user_consents")).isEqualTo("0");
        assertThat(scalar("SELECT provider_subject FROM user_identities WHERE provider = 'LOCAL' AND provider_email LIKE ' Active.Local%'"))
                .isEqualTo("active.local@example.com");
        assertThat(scalar("SELECT provider_subject FROM user_identities WHERE provider = 'GOOGLE'"))
                .isEqualTo("Google-Subject");
        assertThat(scalar("SELECT provider_email_verified IS NULL FROM user_identities WHERE provider = 'GOOGLE'"))
                .isEqualTo("1");
        assertThat(scalar("SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'user_identities' AND column_name = 'provider_subject'"))
                .isEqualTo("utf8mb4_bin");
        assertThat(scalar("SELECT COUNT(*) FROM user_identities i LEFT JOIN users u ON u.id = i.user_id WHERE u.id IS NULL"))
                .isEqualTo("0");
        assertThat(scalar("SELECT COUNT(*) FROM (SELECT provider, provider_subject FROM user_identities GROUP BY provider, provider_subject HAVING COUNT(*) > 1) duplicates"))
                .isEqualTo("0");
    }

    @Test
    void allowsLocalUserWithoutPasswordForAuditToHandleLater() throws SQLException {
        migrateToV2();
        execute("INSERT INTO users (email, name, password_hash, status, provider) VALUES ('no-password@example.com', 'local', NULL, 'PENDING_EMAIL', 'LOCAL')");

        flyway(null).migrate();

        assertThat(scalar("SELECT COUNT(*) FROM user_identities WHERE provider = 'LOCAL'"))
                .isEqualTo("1");
    }

    @Test
    void rejectsSocialUserWithoutProviderSubject() throws SQLException {
        migrateToV2();
        execute("INSERT INTO users (email, name, status, provider, provider_user_id) VALUES ('missing@example.com', 'missing', 'ACTIVE', 'GOOGLE', NULL)");

        assertMigrationFailsWith("V3_BACKFILL_MISSING_SOCIAL_SUBJECT");
    }

    @Test
    void rejectsDuplicateProviderSubject() throws SQLException {
        migrateToV2();
        execute("ALTER TABLE users DROP INDEX uk_users_provider_identity");
        execute("""
                INSERT INTO users (email, name, status, provider, provider_user_id)
                VALUES
                    ('github-1@example.com', 'github-1', 'ACTIVE', 'GITHUB', 'same-subject'),
                    ('github-2@example.com', 'github-2', 'ACTIVE', 'GITHUB', 'same-subject')
                """);

        assertMigrationFailsWith("V3_BACKFILL_DUPLICATE_PROVIDER_SUBJECT");
    }

    @Test
    void rejectsUnsupportedLegacyProvider() throws SQLException {
        migrateToV2();
        execute("INSERT INTO users (email, name, status, provider, provider_user_id) VALUES ('kakao@example.com', 'kakao', 'ACTIVE', 'KAKAO', 'subject')");

        assertMigrationFailsWith("V3_BACKFILL_UNSUPPORTED_PROVIDER");
    }

    private void migrateToV2() {
        flyway("2").migrate();
    }

    private void assertMigrationFailsWith(String marker) {
        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining(marker);
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
