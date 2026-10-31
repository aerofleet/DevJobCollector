package kr.itsdev.devjobcollector.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="DJC_MIGRATION_TEST_URL",matches=".+")
class CompanyJobV12MigrationTest {
    private static final String SCHEMA="djc_company_v12_"+UUID.randomUUID().toString().replace("-","");
    private static Flyway flyway(String target) {
        var config=Flyway.configure().dataSource(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME","root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD",""))
                .schemas(SCHEMA).defaultSchema(SCHEMA).locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) config.target(target);
        return config.load();
    }
    @BeforeEach void clean() { flyway(null).clean(); }
    @AfterAll static void cleanup() { flyway(null).clean(); }
    @Test void cleanInstallCreatesThreeTablesAndDirectSource() throws Exception {
        flyway(null).migrate();
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('company_jobs','company_job_audits','company_job_events')")).isEqualTo("3");
        assertThat(scalar("SELECT column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='job_posts' AND column_name='source_platform'")).contains("COMPANY_DIRECT");
    }
    @Test void upgradeFromV11PreservesExistingSourceAndPublicRow() throws Exception {
        flyway("11").migrate();
        execute("INSERT INTO job_posts (source_platform, original_sn, company_name, title, start_date, end_date, original_url, is_active) VALUES ('SARAMIN','v12-preserve','기업','기존 공고','2026-01-01','2030-12-31','https://example.com/jobs',true)");
        flyway(null).migrate();
        assertThat(scalar("SELECT source_platform FROM job_posts WHERE original_sn='v12-preserve'")).isEqualTo("SARAMIN");
        assertThat(scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version='12' AND success=1")).isEqualTo("1");
    }
    @Test void upgradeFromV35DoesNotInventSearchEvidenceForExistingJobs() throws Exception {
        flyway("35").migrate();
        execute("INSERT INTO job_posts (source_platform, original_sn, company_name, title, start_date, end_date, original_url, is_active) VALUES ('COMPANY_DIRECT','seo-existing','기업','기존 공고','2026-01-01','2030-12-31','https://example.com/jobs',true)");
        flyway(null).migrate();
        assertThat(scalar("SELECT COUNT(*) FROM job_posts WHERE original_sn='seo-existing' AND original_posted_date IS NULL AND complete_description_confirmed=0")).isEqualTo("1");
    }
    private static Connection connection() throws SQLException {
        Connection c=DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME","root"),System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD",""));
        c.setCatalog(SCHEMA); return c;
    }
    private static String scalar(String sql) throws SQLException {
        try (Connection c=connection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
    private static void execute(String sql) throws SQLException {
        try (Connection c=connection(); Statement s=c.createStatement()) { s.execute(sql); }
    }
}
