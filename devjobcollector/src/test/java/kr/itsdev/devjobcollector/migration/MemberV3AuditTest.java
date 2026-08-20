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
class MemberV3AuditTest {

    private static final Path AUDIT_SQL = Path.of("ops", "db", "audit-member-v3.sql");
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
    void prepareV3Fixture() throws SQLException {
        flyway().clean();
        flyway().migrate();
        execute("""
                INSERT INTO users
                    (email, name, password_hash, status, provider, provider_user_id, email_verified_at)
                VALUES
                    (' Active.Local@Example.com ', 'active-local', 'hash-1', 'ACTIVE', 'LOCAL', NULL, CURRENT_TIMESTAMP(6)),
                    ('pending@example.com', 'pending-local', 'hash-2', 'PENDING_EMAIL', 'LOCAL', NULL, NULL),
                    ('google@example.com', 'google-user', NULL, 'ACTIVE', 'GOOGLE', 'Google-Subject', CURRENT_TIMESTAMP(6)),
                    ('github@example.com', 'github-user', NULL, 'ACTIVE', 'GITHUB', 'GitHub-Subject', CURRENT_TIMESTAMP(6))
                """);
        execute("""
                INSERT INTO user_identities
                    (user_id, provider, provider_subject, provider_email, provider_email_verified)
                SELECT id, provider,
                       CASE WHEN provider = 'LOCAL' THEN LOWER(TRIM(email)) ELSE provider_user_id END,
                       email,
                       CASE WHEN provider = 'LOCAL' THEN email_verified_at IS NOT NULL ELSE NULL END
                FROM users
                """);
        execute("INSERT INTO personal_profiles (user_id) SELECT id FROM users WHERE status = 'ACTIVE'");
    }

    @AfterAll
    static void cleanAfterTests() {
        if (url != null) {
            flyway().clean();
        }
    }

    @Test
    void reportsZeroDefectsForValidBackfill() throws Exception {
        Map<String, Long> audit = audit();

        assertThat(audit).containsEntry("users_total", 4L)
                .containsEntry("identity_total", 4L)
                .containsEntry("profile_total", 3L)
                .containsEntry("legacy_consent_rows", 0L);
        assertThat(audit.entrySet())
                .filteredOn(entry -> !entry.getKey().endsWith("_total")
                        && !entry.getKey().equals("legacy_consent_rows"))
                .allMatch(entry -> entry.getValue() == 0L);
    }

    @Test
    void detectsLegacyCredentialAndSubjectDefects() throws Exception {
        execute("UPDATE users SET password_hash = NULL WHERE provider = 'LOCAL' AND status = 'PENDING_EMAIL'");
        execute("UPDATE users SET provider_user_id = NULL WHERE provider = 'GOOGLE'");
        execute("UPDATE users SET provider = 'KAKAO' WHERE provider = 'GITHUB'");

        assertThat(audit())
                .containsEntry("legacy_local_password_missing_count", 1L)
                .containsEntry("legacy_social_subject_missing_count", 1L)
                .containsEntry("legacy_unsupported_provider_count", 1L)
                .containsEntry("legacy_primary_identity_mismatch_count", 2L);
    }

    @Test
    void detectsMissingAndMismatchedIdentities() throws Exception {
        execute("DELETE FROM user_identities WHERE provider = 'GITHUB'");
        execute("UPDATE user_identities SET provider_subject = 'wrong-subject' WHERE provider = 'GOOGLE'");

        assertThat(audit())
                .containsEntry("user_missing_identity_count", 1L)
                .containsEntry("legacy_primary_identity_mismatch_count", 1L);
    }

    @Test
    void detectsIdentityDuplicateExcessAfterConstraintDrift() throws Exception {
        execute("ALTER TABLE user_identities ADD INDEX idx_audit_user_id (user_id)");
        execute("ALTER TABLE user_identities DROP INDEX uk_user_identities_provider_subject");
        execute("ALTER TABLE user_identities DROP INDEX uk_user_identities_user_provider");
        execute("""
                INSERT INTO user_identities (user_id, provider, provider_subject)
                SELECT id, 'GOOGLE', 'Google-Subject'
                FROM users
                WHERE provider = 'GOOGLE'
                """);

        assertThat(audit())
                .containsEntry("identity_provider_subject_duplicate_count", 1L)
                .containsEntry("identity_user_provider_duplicate_count", 1L);
    }

    @Test
    void detectsOrphansAndProfileConsentIntegrityDefects() throws Exception {
        execute("DELETE FROM personal_profiles WHERE user_id = (SELECT id FROM users WHERE provider = 'GOOGLE')");
        execute("INSERT INTO personal_profiles (user_id) SELECT id FROM users WHERE status = 'PENDING_EMAIL'");
        execute("UPDATE personal_profiles SET profile_status = 'UNKNOWN' WHERE user_id = (SELECT id FROM users WHERE status = 'PENDING_EMAIL')");
        execute("""
                INSERT INTO user_consents (user_id, consent_type, policy_version, action, occurred_at)
                SELECT id, 'UNKNOWN', '', 'INVALID', CURRENT_TIMESTAMP(6)
                FROM users
                WHERE provider = 'LOCAL'
                LIMIT 1
                """);
        executeWithoutForeignKeyChecks(
                "INSERT INTO user_identities (user_id, provider, provider_subject) VALUES (900001, 'APPLE', 'orphan-identity')",
                "INSERT INTO personal_profiles (user_id) VALUES (900002)",
                "INSERT INTO user_consents (user_id, consent_type, policy_version, action, occurred_at) VALUES (900003, 'MARKETING', 'v1', 'ACCEPTED', CURRENT_TIMESTAMP(6))"
        );

        assertThat(audit())
                .containsEntry("identity_orphan_count", 1L)
                .containsEntry("profile_orphan_count", 1L)
                .containsEntry("consent_orphan_count", 1L)
                .containsEntry("active_user_missing_profile_count", 1L)
                .containsEntry("non_active_user_with_profile_count", 1L)
                .containsEntry("profile_invalid_status_count", 1L)
                .containsEntry("consent_invalid_type_count", 1L)
                .containsEntry("consent_invalid_action_count", 1L)
                .containsEntry("consent_blank_policy_version_count", 1L);
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
