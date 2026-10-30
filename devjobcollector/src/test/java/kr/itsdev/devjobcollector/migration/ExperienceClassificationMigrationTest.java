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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import kr.itsdev.devjobcollector.service.PublicDataApiClient;
import kr.itsdev.devjobcollector.service.PublicJobDetailsBackfillService;
import kr.itsdev.devjobcollector.dto.PublicDataDetailResponse;
import kr.itsdev.devjobcollector.dto.PublicJobDto;
import static org.mockito.Mockito.*;

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

    @Test void migratesTossExperienceWithoutTreatingPreferredOrReviewedAsRequired() throws Exception {
        flyway("33").migrate();
        try (Connection c = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            c.setCatalog(SCHEMA);
            try (var insert = c.prepareStatement("INSERT INTO job_posts (source_platform,original_sn,company_name,title,experience,apply_qual,start_date,end_date,original_url,is_active,reviewed_experience_category) VALUES ('COMPANY_PAGE',?,'토스','공고','경력무관',?,'2026-01-01','2030-01-01','https://example.com',1,?)")) {
                String[] ids = {"toss:req", "toss:preferred", "toss:reviewed"};
                String[] texts = {"# 이런 분과 함께하고 싶어요\n- 업무를 2년 이상 경험하신 분\n# 이런 경험이 있다면 더 좋아요\n- 7년 이상 경험", "# 이런 분과 함께하고 싶어요\n- 협업 능력\n# 이런 경험이 있다면 더 좋아요\n- 7년 이상 경험", "# 이런 분과 함께하고 싶어요\n- 2년 이상 경험"};
                for (int i = 0; i < ids.length; i++) { insert.setString(1, ids[i]); insert.setString(2, texts[i]); insert.setString(3, i == 2 ? "ENTRY" : null); insert.executeUpdate(); }
            }
        }
        flyway(null).migrate();
        try (Connection c = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            c.setCatalog(SCHEMA);
            try (var statement = c.createStatement(); var rows = statement.executeQuery("SELECT original_sn,experience_category,experience_label FROM job_posts WHERE original_sn LIKE 'toss:%' ORDER BY original_sn")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("UNKNOWN");
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("EXPERIENCED"); assertThat(rows.getString(3)).isEqualTo("경력 2년 이상");
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("UNKNOWN");
            }
        }
    }


    @Test void salaryEvidenceMigrationKeepsExistingSourceData() throws Exception {
        flyway("32").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,apply_qual,start_date,end_date,original_url,is_active) VALUES ('GREENHOUSE','stripe:8330801','Stripe','Engineer','Original description','2026-01-01','2030-01-01','https://stripe.com/jobs/search?gh_jid=8330801',1)");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT apply_qual,supplemental_salary_text,supplemental_salary_source_hash FROM job_posts WHERE original_sn='stripe:8330801'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Original description");
                assertThat(rs.getString(2)).isNull();
                assertThat(rs.getString(3)).isNull();
            }
        }
    }

    @Test void backfillsPublicDetailsAndRetriesFailuresWithoutTouchingOtherSources() throws Exception {
        flyway(null).migrate();
        var ds = new DriverManagerDataSource(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        var jdbc = new JdbcTemplate(ds);
        // Set the schema per connection, since JdbcTemplate opens a new connection for each statement.
        ds.setUrl(System.getenv("DJC_MIGRATION_TEST_URL").replaceAll("/[^/?]*(\\?.*)?$", "/" + SCHEMA + "$1"));
        jdbc.update("INSERT INTO job_posts (source_platform,original_sn,company_name,title,start_date,end_date,original_url,is_active) VALUES "
                + "('PUBLIC_ALIO','good','기관','공고','2026-01-01','2030-01-01','https://example.com',1),"
                + "('PUBLIC_ALIO','bad','기관','공고','2026-01-01','2030-01-01','https://example.com',1),"
                + "('GREENHOUSE','other','기관','공고','2026-01-01','2030-01-01','https://example.com',1)");
        var api = mock(PublicDataApiClient.class);
        var dto = PublicJobDto.builder().recrutPblntSn("good").prefCn("우대 내용").disqlfcRsn("결격 사유").build();
        var response = new PublicDataDetailResponse(200, "ok", 1, dto);
        when(api.fetchJobDetail("good")).thenReturn(response);
        when(api.fetchJobDetail("bad")).thenReturn(null);
        var backfill = new PublicJobDetailsBackfillService(jdbc, api);
        ReflectionTestUtils.setField(backfill, "collectionEnabled", true);
        backfill.backfill();
        assertThat(jdbc.queryForObject("SELECT public_preference FROM job_posts WHERE original_sn='good'", String.class)).isEqualTo("우대 내용");
        assertThat(jdbc.queryForObject("SELECT public_disqualification FROM job_posts WHERE original_sn='good'", String.class)).isEqualTo("결격 사유");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_posts WHERE original_sn='bad' AND public_details_checked_at IS NULL AND public_details_attempted_at IS NOT NULL", Integer.class)).isEqualTo(1);
        backfill.backfill();
        verify(api, times(1)).fetchJobDetail("good");
        verify(api, times(1)).fetchJobDetail("bad");
        verify(api, never()).fetchJobDetail("other");
    }

    @Test void reclassifiesExpertiseWithoutOverwritingReviewedRows() throws Exception {
        flyway("30").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,experience,apply_qual,start_date,end_date,original_url,is_active,reviewed_experience_category) VALUES "
                        + "('GREENHOUSE','expertise-a','기업','Accountant','경력무관','<p>Your Expertise:</p><li>4+ years of accounting experience</li><li>Preferred: 8+ years of accounting experience</li>','2026-01-01','2030-01-01','https://example.com',1,NULL),"
                        + "('GREENHOUSE','expertise-b','기업','Accountant','경력무관','<p>Your Expertise:</p><li>4+ years of accounting experience</li>','2026-01-01','2030-01-01','https://example.com',1,'ANY')");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT experience_category,experience_label FROM job_posts WHERE original_sn IN ('expertise-a','expertise-b') ORDER BY original_sn")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience_category")).isEqualTo("EXPERIENCED");
                assertThat(rows.getString("experience_label")).isEqualTo("경력 4년 이상");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience_category")).isEqualTo("UNKNOWN");
            }
        }
    }

    @Test void reclassifiesRecruitingHeadingAndPreservesReviewedRows() throws Exception {
        flyway("29").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,experience,apply_qual,start_date,end_date,original_url,is_active,reviewed_experience_category) VALUES "
                        + "('GREENHOUSE','figma-a','기업','Manager','경력무관','<p>We&#39;d love to hear from you if you have:</p><li>3+ years of experience leading teams</li><p>While not required, it&#39;s an added plus if you also have:</p><li>8+ years of experience</li>','2026-01-01','2030-01-01','https://example.com',1,NULL),"
                        + "('GREENHOUSE','figma-b','기업','Manager','경력무관','<p>We&#39;d love to hear from you if you have:</p><li>3+ years of experience leading teams</li>','2026-01-01','2030-01-01','https://example.com',1,'ANY')");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT experience_category,experience_label FROM job_posts WHERE original_sn IN ('figma-a','figma-b') ORDER BY original_sn")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience_category")).isEqualTo("EXPERIENCED");
                assertThat(rows.getString("experience_label")).isEqualTo("경력 3년 이상");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience_category")).isEqualTo("UNKNOWN");
            }
        }
    }

    @Test void reclassifiesMinimumRangeWithoutOverwritingReviewedMetadata() throws Exception {
        flyway("28").migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_posts (source_platform,original_sn,company_name,title,experience,apply_qual,start_date,end_date,original_url,is_active,reviewed_experience_category) VALUES "
                        + "('GREENHOUSE','range','기업','Support','경력무관','<p>Job requirements:</p><li>Minimum of 2-3 years of relevant experience.</li>','2026-01-01','2030-01-01','https://example.com',1,NULL),"
                        + "('GREENHOUSE','reviewed','기업','Support','경력무관','<p>Job requirements:</p><li>Minimum of 2-3 years of relevant experience.</li>','2026-01-01','2030-01-01','https://example.com',1,'ANY')");
            }
        }
        flyway(null).migrate();
        try (Connection connection = DriverManager.getConnection(System.getenv("DJC_MIGRATION_TEST_URL"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""))) {
            connection.setCatalog(SCHEMA);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT original_sn,experience_category,experience_label FROM job_posts ORDER BY original_sn")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience_category")).isEqualTo("EXPERIENCED");
                assertThat(rows.getString("experience_label")).isEqualTo("경력 최소 2~3년");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("experience_category")).isEqualTo("UNKNOWN");
            }
        }
    }

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
                    "SELECT location,reviewed_location,reviewed_source_hash FROM job_posts WHERE original_sn='test:28'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("location")).isEqualTo("Remote, United States");
                assertThat(rows.getString("reviewed_location")).isNull();
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
