package kr.itsdev.devjobcollector.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
class ExperienceClassificationMigrationTest {
    private static final String SCHEMA = "djc_exp_" + UUID.randomUUID().toString().replace("-", "");
    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))
                .schemas(SCHEMA).defaultSchema(SCHEMA).locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) config.target(target);
        return config.load();
    }
    @AfterAll static void cleanup() { flyway(null).clean(); }

    @Test void backfillsExistingRowsWithoutChangingSourceData() throws Exception {
        flyway("20").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,experience,apply_qual,start_date,end_date,original_url,is_active) VALUES "
                        + "('GREENHOUSE','required','기업','개발자','경력무관','자격요건\\n실무 경력 7년 이상','2026-01-01','2030-01-01','https://example.com',1),"
                        + "('GREENHOUSE','unknown','기업','개발자','경력무관','우대사항\\n실무 경력 5년 이상','2026-01-01','2030-01-01','https://example.com',1)");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT original_sn,experience,experience_category,experience_label FROM job_posts ORDER BY original_sn")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience")).isEqualTo("경력무관");
                assertThat(rows.getString("experience_category")).isEqualTo("EXPERIENCED");
                assertThat(rows.getString("experience_label")).isEqualTo("경력 7년 이상");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience_category")).isEqualTo("UNKNOWN");
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
