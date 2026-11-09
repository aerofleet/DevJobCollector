package kr.itsdev.devjobcollector.jobfile;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.domain.JobFile;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = { "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import(QuerydslConfig.class)
class JobFilePreviewMysqlIntegrationTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @Test void previewOnlyExposesFilesBelongingToVisibleActiveJobs() {
        var job = JobPost.builder().sourcePlatform(SourcePlatform.PUBLIC_ALIO).originalSn("preview-policy-test")
                .companyName("테스트").title("테스트 공고").experience("신입").startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(1)).originalUrl("https://example.com").build();
        var file = JobFile.builder().fileName("공고.hwp").fileUrl("https://www.alio.go.kr/download/download.json?fileNo=123").build();
        job.addFile(file);
        em.persist(job); em.flush();
        var controller = new JobFilePreviewController(jdbc, new SimpleMeterRegistry(), WebClient.create());
        assertThat(controller.preview(file.getId()).getStatusCode().value()).isEqualTo(200);
        jdbc.update("UPDATE job_posts SET moderation_status='HIDDEN' WHERE id=?", job.getId());
        assertThatThrownBy(() -> controller.preview(file.getId())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        jdbc.update("UPDATE job_posts SET moderation_status='ACTIVE', is_active=0 WHERE id=?", job.getId());
        assertThatThrownBy(() -> controller.preview(file.getId())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        jdbc.update("UPDATE job_posts SET is_active=1, end_date='2000-01-01' WHERE id=?", job.getId());
        assertThatThrownBy(() -> controller.preview(file.getId())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

}
