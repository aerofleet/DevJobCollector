package kr.itsdev.devjobcollector.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
class JobSearchQualityEvaluationIntegrationTest {

    private static final String EVALUATION_SET = "/search/job-search-evaluation-set.csv";

    @Autowired JobPostRepository repository;
    @Autowired TechStackRepository techStackRepository;
    @Autowired EntityManager entityManager;

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
    void meetsRecallRankingAndZeroResultKpisForOneHundredQueries() throws IOException {
        seedEvaluationCorpus();
        entityManager.flush();
        entityManager.clear();

        List<EvaluationCase> cases = loadEvaluationCases();
        assertEvaluationSetShape(cases);

        double recallSum = 0.0;
        double ndcgSum = 0.0;
        int zeroResults = 0;
        for (EvaluationCase evaluationCase : cases) {
            List<String> resultIds = search(evaluationCase.query()).stream()
                    .map(JobPost::getOriginalSn)
                    .toList();
            zeroResults += resultIds.isEmpty() ? 1 : 0;
            recallSum += recallAt20(resultIds, evaluationCase.relevance());
            ndcgSum += ndcgAt10(resultIds, evaluationCase.relevance());
        }

        double recallAt20 = recallSum / cases.size();
        double ndcgAt10 = ndcgSum / cases.size();
        double zeroResultRate = (double) zeroResults / cases.size();
        System.out.printf(
                "job-search-quality queries=%d recall@20=%.3f ndcg@10=%.3f zero-result-rate=%.3f%n",
                cases.size(), recallAt20, ndcgAt10, zeroResultRate);

        assertThat(recallAt20).isGreaterThanOrEqualTo(0.85);
        assertThat(ndcgAt10).isGreaterThanOrEqualTo(0.80);
        assertThat(zeroResultRate).isLessThan(0.05);
    }

    private void assertEvaluationSetShape(List<EvaluationCase> cases) {
        assertThat(cases).hasSize(100);
        assertThat(cases.stream().collect(Collectors.groupingBy(
                EvaluationCase::category, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "MIXED", 30L,
                        "COMPOUND", 30L,
                        "SYNONYM", 20L,
                        "BODY_ONLY", 20L));
    }

    private List<JobPost> search(String keyword) {
        return repository.searchByAllFieldsOptimized(
                        keyword, null, null, null, null, LocalDate.now(),
                        PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))))
                .getContent();
    }

    private double recallAt20(List<String> resultIds, Map<String, Integer> relevance) {
        long found = relevance.keySet().stream().filter(resultIds::contains).count();
        return (double) found / relevance.size();
    }

    private double ndcgAt10(List<String> resultIds, Map<String, Integer> relevance) {
        double dcg = 0.0;
        for (int index = 0; index < Math.min(10, resultIds.size()); index++) {
            dcg += gain(relevance.getOrDefault(resultIds.get(index), 0)) / log2(index + 2);
        }
        List<Integer> ideal = relevance.values().stream()
                .sorted((left, right) -> Integer.compare(right, left))
                .limit(10)
                .toList();
        double idealDcg = 0.0;
        for (int index = 0; index < ideal.size(); index++) {
            idealDcg += gain(ideal.get(index)) / log2(index + 2);
        }
        return idealDcg == 0.0 ? 1.0 : dcg / idealDcg;
    }

    private double gain(int grade) {
        return Math.pow(2, grade) - 1;
    }

    private double log2(int value) {
        return Math.log(value) / Math.log(2);
    }

    private List<EvaluationCase> loadEvaluationCases() throws IOException {
        var stream = getClass().getResourceAsStream(EVALUATION_SET);
        assertThat(stream).as("evaluation set %s", EVALUATION_SET).isNotNull();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank())
                    .map(this::parseEvaluationCase)
                    .toList();
        }
    }

    private EvaluationCase parseEvaluationCase(String line) {
        String[] columns = line.split(",", -1);
        assertThat(columns).as("evaluation row: %s", line).hasSize(3);
        Map<String, Integer> relevance = Arrays.stream(columns[2].split(";"))
                .map(value -> value.split(":", -1))
                .collect(Collectors.toMap(
                        value -> value[0],
                        value -> Integer.parseInt(value[1]),
                        (left, right) -> left,
                        LinkedHashMap::new));
        return new EvaluationCase(columns[0], columns[1], relevance);
    }

    private void seedEvaluationCorpus() {
        saveJob("backend-java", "Java Backend Engineer", "백엔드 개발",
                "Spring Boot REST API Kafka microservices", "coding test technical interview",
                "서울", "경력", "정규직", "Java", "Spring");
        saveJob("frontend-react", "React Frontend Developer", "프론트엔드 Web 개발",
                "TypeScript CSS accessibility", "portfolio review",
                "부산", "신입", "정규직", "React", "TypeScript");
        saveJob("fullstack-node", "Fullstack Developer", "풀스택 개발",
                "Node React PostgreSQL cloud", "culture interview",
                "서울", "경력", "정규직", "Node", "AWS");
        saveJob("devops-k8s", "DevOps SRE Engineer", "인프라 플랫폼",
                "Kubernetes Docker Terraform CI/CD observability", "system design interview",
                "서울", "경력", "정규직", "Kubernetes", "Docker");
        saveJob("ml-ai", "Machine Learning AI Engineer", "데이터 AI",
                "Python TensorFlow recommendation NLP 데이터 모델", "research presentation",
                "판교", "경력", "정규직", "Python", "TensorFlow");
        saveJob("data-kafka", "Data Engineer Kafka", "데이터 엔지니어링",
                "Spark ETL Airflow MySQL", "SQL coding test",
                "서울", "신입·경력", "정규직", "Kafka", "MySQL");
        saveJob("mobile-android", "Android Developer", "모바일 안드로이드 개발",
                "Kotlin Jetpack Compose", "app portfolio",
                "서울", "경력", "정규직", "Kotlin");
        saveJob("mobile-ios", "iOS Developer", "모바일 아이폰 개발",
                "Swift SwiftUI", "app portfolio",
                "서울", "경력", "정규직", "Swift");
        saveJob("security-oauth", "Security Engineer", "보안 네트워크",
                "OAuth zero trust penetration", "security interview",
                "서울", "경력", "정규직");
        saveJob("qa-automation", "QA Automation Engineer", "품질 테스트",
                "Selenium Playwright automation", "test assignment",
                "서울", "신입·경력", "정규직");
        saveJob("body-platform", "Platform Engineer", "플랫폼 개발",
                "GraphQL Redis MongoDB event sourcing gRPC", "과제 전형 페어 프로그래밍",
                "서울", "경력", "정규직");
        saveJob("public-it", "공공기관 전산 담당", "공공 데이터",
                "정보처리기사 공공데이터", "서류전형 필기시험 면접",
                "대전", "신입", "계약직");
        saveJob("ranking-title", "Kafka Platform Engineer", "메시징 플랫폼",
                "distributed messaging", "technical interview",
                "서울", "경력", "정규직");
        saveJob("ranking-body", "Cloud Platform Engineer", "클라우드 플랫폼",
                "Kafka operations", "technical interview",
                "서울", "경력", "정규직");
    }

    private void saveJob(
            String originalSn, String title, String jobCategory, String applyQual,
            String processInfo, String location, String experience, String hireType,
            String... techStacks
    ) {
        JobPost jobPost = repository.save(JobPost.builder()
                .sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn(originalSn)
                .companyName("검색 평가 기업")
                .title(title)
                .jobCategory(jobCategory)
                .experience(experience)
                .location(location)
                .hireType(hireType)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(30))
                .originalUrl("https://example.com/jobs/" + originalSn)
                .applyQual(applyQual)
                .processInfo(processInfo)
                .build());
        for (String stack : techStacks) {
            jobPost.addTechStack(techStackRepository.save(
                    TechStack.builder().stackName(stack + "-" + originalSn).build()));
        }
        repository.save(jobPost);
    }

    private record EvaluationCase(String category, String query, Map<String, Integer> relevance) {
    }
}
