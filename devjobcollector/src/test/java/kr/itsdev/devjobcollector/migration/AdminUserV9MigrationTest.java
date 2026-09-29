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
class AdminUserV9MigrationTest {
    private static String url;
    private static String username;
    private static String password;

    @BeforeAll
    static void configure() {
        url = System.getenv("DJC_MIGRATION_TEST_URL");
        username = System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root");
        password = System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", "");
    }

    @BeforeEach
    void clean() { flyway(null).clean(); }

    @AfterAll
    static void cleanAfter() {
        if (url != null) flyway(null).clean();
    }

    @Test
    void cleanInstallCreatesRevocationColumn() throws SQLException {
        flyway(null).migrate();
        assertThat(scalar("SELECT version FROM flyway_schema_history WHERE success = 1 "
                + "ORDER BY installed_rank DESC LIMIT 1")).isEqualTo("9");
        assertThat(scalar("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'users'
                  AND column_name = 'session_revoked_at'
                """)).isEqualTo("1");
    }

    @Test
    void upgradePreservesExistingMemberAndDefaultsRevocationToNull() throws SQLException {
        flyway("8").migrate();
        execute("""
                INSERT INTO users (id, email, name, role, status, provider)
                VALUES (901, 'upgrade@example.com', 'Upgrade', 'USER', 'ACTIVE', 'LOCAL')
                """);
        flyway(null).migrate();

        assertThat(scalar("SELECT status FROM users WHERE id = 901")).isEqualTo("ACTIVE");
        assertThat(scalar("SELECT session_revoked_at FROM users WHERE id = 901")).isNull();
        assertThat(scalar("SELECT COUNT(*) FROM users WHERE id = 901")).isEqualTo("1");
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(target);
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
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
