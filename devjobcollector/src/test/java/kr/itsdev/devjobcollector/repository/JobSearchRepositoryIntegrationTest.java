package kr.itsdev.devjobcollector.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.domain.TechStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import(QuerydslConfig.class)
class JobSearchRepositoryIntegrationTest {

    @Autowired JobPostRepository repository;
    @Autowired TechStackRepository techStackRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @Test
    void searchesDescriptionProcessAndEmploymentType() {
        saveJob("description", "서비스 개발자", "Kafka 스트림 처리", null, "정규직");
        saveJob("process", "플랫폼 개발자", null, "코딩테스트 후 기술면접", "계약직");
        entityManager.flush();
        entityManager.clear();

        assertThat(search("Kafka")).extracting(JobPost::getOriginalSn).containsExactly("description");
        assertThat(search("기술면접")).extracting(JobPost::getOriginalSn).containsExactly("process");
        assertThat(search("계약직")).extracting(JobPost::getOriginalSn).containsExactly("process");
    }

    @Test
    void matchesAllNormalizedTokensAcrossDifferentFields() {
        saveJob("match", "Java 백엔드 개발자", "대규모 Kafka 파이프라인 운영", null, "정규직");
        saveJob("missing", "Java 백엔드 개발자", "REST API 개발", null, "정규직");
        entityManager.flush();
        entityManager.clear();

        assertThat(search("JAVA/Kafka"))
                .extracting(JobPost::getOriginalSn)
                .containsExactly("match");
    }

    @Test
    void ranksTitleMatchAheadOfDescriptionOnlyMatch() {
        saveJob("body-match", "데이터 엔지니어", "Kafka 운영 경험 우대", null, "정규직");
        saveJob("title-match", "Kafka 플랫폼 엔지니어", "메시징 시스템 운영", null, "정규직");
        entityManager.flush();
        entityManager.clear();

        assertThat(search("Kafka"))
                .extracting(JobPost::getOriginalSn)
                .containsExactly("title-match", "body-match");
    }

    @Test
    void matchesMultipleTokensAcrossDifferentTechStackTags() {
        JobPost tagged = saveJob(
                "tagged", "백엔드 개발자", "대규모 서비스 운영", null, "정규직");
        tagged.addTechStack(techStackRepository.save(TechStack.builder().stackName("Java").build()));
        tagged.addTechStack(techStackRepository.save(TechStack.builder().stackName("Spring").build()));
        repository.save(tagged);
        saveJob("untagged", "백엔드 개발자", "Java 애플리케이션 운영", null, "정규직");
        entityManager.flush();
        entityManager.clear();

        assertThat(search("Java Spring"))
                .extracting(JobPost::getOriginalSn)
                .containsExactly("tagged");
    }

    @Test
    void expandsDeveloperSynonymsAgainstStoredDescription() {
        saveJob("synonym", "플랫폼 엔지니어", "Kubernetes 클러스터를 운영합니다.", null, "정규직");
        entityManager.flush();
        entityManager.clear();

        assertThat(search("k8s"))
                .extracting(JobPost::getOriginalSn)
                .containsExactly("synonym");
    }

    @Test
    void keepsP95BelowThreeHundredMillisecondsForProductionSizedDataset() {
        seedPerformanceJobs(1_000);
        entityManager.flush();
        entityManager.clear();

        for (int warmup = 0; warmup < 5; warmup++) {
            search("backend k8s");
        }

        List<Long> elapsedMillis = new ArrayList<>();
        for (int iteration = 0; iteration < 30; iteration++) {
            long startedAt = System.nanoTime();
            assertThat(search("backend k8s")).hasSize(20);
            elapsedMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        }
        Collections.sort(elapsedMillis);
        long p50 = elapsedMillis.get((int) Math.ceil(elapsedMillis.size() * 0.50) - 1);
        long p95 = elapsedMillis.get((int) Math.ceil(elapsedMillis.size() * 0.95) - 1);
        System.out.printf("job-search-performance rows=1000 requests=30 p50=%dms p95=%dms max=%dms%n",
                p50, p95, elapsedMillis.getLast());

        assertThat(p95).isLessThan(300L);
    }

    private java.util.List<JobPost> search(String keyword) {
        return repository.searchByAllFieldsOptimized(
                        keyword, null, null, null, null, LocalDate.now(),
                        PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))))
                .getContent();
    }

    private JobPost saveJob(
            String originalSn,
            String title,
            String applyQual,
            String processInfo,
            String hireType
    ) {
        return repository.save(JobPost.builder()
                .sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn(originalSn)
                .companyName("검색 평가 기업")
                .title(title)
                .jobCategory("IT개발·데이터")
                .experience("신입·경력")
                .location("서울")
                .hireType(hireType)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(30))
                .originalUrl("https://example.com/jobs/" + originalSn)
                .applyQual(applyQual)
                .processInfo(processInfo)
                .build());
    }

    private void seedPerformanceJobs(int count) {
        String sql = """
                INSERT INTO job_posts (
                    source_platform, original_sn, company_name, title, job_category,
                    experience, location, hire_type, start_date, end_date, original_url,
                    apply_qual, process_info, is_active, created_at
                ) VALUES ('SARAMIN', ?, '성능 평가 기업', ?, 'IT개발·데이터',
                    '신입·경력', '서울', '정규직', ?, ?, ?, ?, NULL, 1, NOW(6))
                """;
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(30);
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                boolean matching = index % 2 == 0;
                statement.setString(1, "performance-" + index);
                statement.setString(2, matching ? "Backend Engineer " + index : "Product Manager " + index);
                statement.setObject(3, startDate);
                statement.setObject(4, endDate);
                statement.setString(5, "https://example.com/jobs/performance-" + index);
                statement.setString(6, matching
                        ? "Kubernetes 기반 대규모 플랫폼을 운영합니다."
                        : "사업 전략과 고객 경험을 개선합니다.");
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });
    }
}
