package kr.itsdev.devjobcollector.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
    @BeforeEach void resetSchema() { flyway(null).clean(); }

    @Test void preservesOriginalLocationWhenAddingReviewFields() throws Exception {
        flyway("27").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,location,start_date,end_date,original_url,is_active) VALUES ('GREENHOUSE','test:28','기업','공고','Remote, United States','2026-01-01','2030-01-01','https://example.com',1)");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT location,effective_location,reviewed_source_hash FROM job_posts WHERE original_sn='test:28'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("location")).isEqualTo("Remote, United States");
                assertThat(rows.getString("effective_location")).isEqualTo("Remote, United States");
                assertThat(rows.getString("reviewed_source_hash")).isNull();
            }
        }
    }

    @Test void createsReviewTableOnUpgradeFromV26() throws Exception {
        flyway("26").migrate();
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='job_metadata_ai_review' AND column_name IN ('input_hash','experience_quote','location_quote','review_status')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(4);
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,start_date,end_date,original_url,is_active) VALUES ('GREENHOUSE','review:1','기업','공고','2026-01-01','2030-01-01','https://example.com',1)");
                String insert = "INSERT INTO job_metadata_ai_review (job_post_id,job_version,input_hash,source_key,model,experience_suggestion,experience_quote,location_suggestion,location_quote,review_status,requested_by,created_at) "
                        + "SELECT id,version,'hash','GREENHOUSE:review','model','경력 3년','3년','','','NEEDS_REVIEW',1,NOW(6) FROM job_posts WHERE original_sn='review:1' AND version=";
                assertThat(statement.executeUpdate(insert + "0")).isEqualTo(1);
                assertThat(statement.executeUpdate(insert + "99")).isZero();
            }
        }
    }

    @Test void reclassifiesEncodedHtmlAfterV24() throws Exception {
        flyway("24").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,experience,apply_qual,start_date,end_date,original_url,is_active) VALUES "
                        + "('GREENHOUSE','html-10339','기업','ML Engineer','경력무관','&lt;p&gt;&lt;strong&gt;자격&lt;/strong&gt; &lt;strong&gt;요건&lt;/strong&gt;&lt;/p&gt;&lt;ul&gt;&lt;li&gt;&lt;strong&gt;3&lt;/strong&gt;&lt;strong&gt;년 이상&lt;/strong&gt;의 상용 환경 내 머신러닝, 자연어 처리(NLP) 또는 AI 모델 개발, 학습 및 배포 실무 경력을 보유하신 분&lt;/li&gt;&lt;/ul&gt;','2026-01-01','2030-01-01','https://example.com',1)");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT experience,experience_category,experience_label,experience_evidence,experience_method FROM job_posts WHERE original_sn='html-10339'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience")).isEqualTo("경력무관");
                assertThat(rows.getString("experience_category")).isEqualTo("EXPERIENCED");
                assertThat(rows.getString("experience_label")).isEqualTo("경력 3년 이상");
                assertThat(rows.getString("experience_evidence")).contains("3년 이상의", "실무 경력");
                assertThat(rows.getString("experience_method")).isEqualTo("RULE");
            }
        }
    }

    @Test void correctsOnlyMatchingDaangnGreenhouseUrls() throws Exception {
        flyway("23").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,start_date,end_date,original_url,is_active) VALUES "
                        + "('GREENHOUSE','daangn:8043877003','당근','개발자','2026-01-01','2030-01-01','https://about.daangn.com?gh_jid=8043877003',1),"
                        + "('GREENHOUSE','other:42','다른 기업','개발자','2026-01-01','2030-01-01','https://about.daangn.com?gh_jid=42',1),"
                        + "('GREENHOUSE','daangn:43','당근','개발자','2026-01-01','2030-01-01','https://about.daangn.com?gh_jid=99',1)");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT original_sn,original_url FROM job_posts ORDER BY original_sn")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("original_url")).isEqualTo("https://about.daangn.com?gh_jid=99");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("original_url")).isEqualTo("https://careers.daangn.com/jobs/role/8043877003/");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("original_url")).isEqualTo("https://about.daangn.com?gh_jid=42");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test void reclassifiesExistingInlineSectionsAfterV22() throws Exception {
        flyway("22").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,experience,apply_qual,start_date,end_date,original_url,is_active) VALUES "
                        + "('KAKAO','inline','기업','개발자','경력무관','◆ 지원자격 - 백엔드 개발 경력 3년 이상인 분 ◆ 우대사항 - 실무 경력 7년 이상인 분','2026-01-01','2030-01-01','https://example.com',1)");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT experience,experience_category,experience_label FROM job_posts WHERE original_sn='inline'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience")).isEqualTo("경력무관");
                assertThat(rows.getString("experience_category")).isEqualTo("EXPERIENCED");
                assertThat(rows.getString("experience_label")).isEqualTo("경력 3년 이상");
            }
        }
    }

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
