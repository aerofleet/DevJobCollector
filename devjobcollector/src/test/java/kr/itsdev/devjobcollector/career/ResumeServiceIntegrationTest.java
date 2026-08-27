package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.dto.career.ResumeStatusUpdateRequest;
import kr.itsdev.devjobcollector.dto.career.ResumeUpsertRequest;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import(QuerydslConfig.class)
class ResumeServiceIntegrationTest {
    @Autowired CareerResumeRepository resumeRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired EntityManager entityManager;

    private CurrentMemberService currentMemberService;
    private ObjectMapper objectMapper;
    private ResumeService service;
    private UserAccount owner;
    private UserAccount other;

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

    @BeforeEach
    void setUp() {
        owner = saveUser("resume-owner@example.com");
        other = saveUser("resume-other@example.com");
        currentMemberService = mock(CurrentMemberService.class);
        when(currentMemberService.requireCurrentMember("owner")).thenReturn(owner);
        when(currentMemberService.requireCurrentMember("other")).thenReturn(other);
        objectMapper = new ObjectMapper();
        service = new ResumeService(currentMemberService, resumeRepository, objectMapper);
    }

    @Test
    void persistsAcrossPersistenceContextAndServiceRecreation() throws Exception {
        var created = service.create("owner", new ResumeUpsertRequest(
                "백엔드 이력서", objectMapper.readTree("{\"basicInfo\":{\"name\":\"홍길동\"}}")));
        Long resumeId = created.id();
        entityManager.flush();
        entityManager.clear();

        ResumeService restartedService = new ResumeService(
                currentMemberService, resumeRepository, new ObjectMapper());
        var restored = restartedService.get("owner", resumeId);

        assertThat(restored.title()).isEqualTo("백엔드 이력서");
        assertThat(restored.content().path("basicInfo").path("name").asText()).isEqualTo("홍길동");
        assertThat(restored.content().path("techStack").isArray()).isTrue();
        assertThat(restored.status()).isEqualTo(ResumeStatus.DRAFT);
        assertThat(restored.createdAt()).isNotNull();
        assertThat(restored.updatedAt()).isNotNull();
    }

    @Test
    void blocksAnotherOwnerForEverySingleResumeOperation() throws Exception {
        var created = service.create("owner", new ResumeUpsertRequest(
                "소유자 이력서", objectMapper.readTree("{}")));
        Long resumeId = created.id();
        entityManager.flush();
        entityManager.clear();

        assertNotFound(() -> service.get("other", resumeId));
        assertNotFound(() -> service.update("other", resumeId, new ResumeUpsertRequest(
                "탈취 시도", objectMapper.readTree("{}"))));
        assertNotFound(() -> service.changeStatus(
                "other", resumeId, new ResumeStatusUpdateRequest(ResumeStatus.ARCHIVED)));
        assertNotFound(() -> service.delete("other", resumeId));

        entityManager.flush();
        entityManager.clear();
        CareerResume preserved = resumeRepository.findByIdAndUser_Id(resumeId, owner.getId()).orElseThrow();
        assertThat(preserved.getTitle()).isEqualTo("소유자 이력서");
        assertThat(preserved.getStatus()).isEqualTo(ResumeStatus.DRAFT);
    }

    private void assertNotFound(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private UserAccount saveUser(String email) {
        return userRepository.saveAndFlush(UserAccount.activeSocial(
                email, email, AuthProvider.GITHUB, "subject-" + email));
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
