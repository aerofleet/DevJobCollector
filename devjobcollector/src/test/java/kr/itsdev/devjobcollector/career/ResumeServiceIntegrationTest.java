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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

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

    @Test
    void companyQueriesEnforceVisibilityApplicationOwnershipAndWithdrawal() throws Exception {
        var company = kr.itsdev.devjobcollector.company.Company.pendingVerification(
                "채용 기업", "채용 기업", "b".repeat(64), "***-**-12345", null, other);
        entityManager.persist(company);
        var post = kr.itsdev.devjobcollector.domain.JobPost.builder()
                .sourcePlatform(kr.itsdev.devjobcollector.domain.SourcePlatform.COMPANY_DIRECT)
                .originalSn("resume-visibility-test").companyName("채용 기업").title("개발자")
                .startDate(java.time.LocalDate.of(2026, 10, 1)).endDate(java.time.LocalDate.of(2030, 12, 31))
                .originalUrl("https://example.com/resume-test").build();
        entityManager.persist(post);
        var job = new kr.itsdev.devjobcollector.company.jobs.CompanyJob(company,
                new kr.itsdev.devjobcollector.company.jobs.CompanyJobContent());
        job.publish(post);
        entityManager.persist(job);
        CareerResume resume = resumeRepository.saveAndFlush(CareerResume.draft(owner, "비공개", "{}"));
        Long id = resume.getId();
        Long companyId = company.getId();
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        assertThat(resumeRepository.findVisibleToCompanyById(companyId, id)).isEmpty();
        assertThat(resumeRepository.findVisibleToCompany(companyId, page).getContent()).isEmpty();
        resume.changeVisibility(ResumeVisibility.PUBLIC);
        entityManager.flush();
        assertThat(resumeRepository.findVisibleToCompanyById(companyId, id)).isPresent();
        assertThat(resumeRepository.findVisibleToCompany(companyId, page).getContent()).hasSize(1);
        resume.changeVisibility(ResumeVisibility.PRIVATE);
        var application = JobApplication.applied(owner, post, java.time.LocalDateTime.now(), null);
        entityManager.persist(application);
        entityManager.flush();
        assertThat(resumeRepository.findVisibleToCompanyById(companyId, id)).isPresent();
        assertThat(resumeRepository.findVisibleToCompany(companyId, page).getContent()).hasSize(1);
        assertThat(resumeRepository.findVisibleToCompanyById(-1L, id)).isEmpty();
        var companyReader = new CompanyResumeService(currentMemberService,
                mock(kr.itsdev.devjobcollector.company.CompanyAuthorizationService.class), resumeRepository, objectMapper);
        var attachments = new ResumeAttachmentService(jdbc, currentMemberService, companyReader);
        var uploaded = attachments.upload("owner", id, pdf());
        assertThat(attachments.listForCompany("other", companyId, id)).hasSize(1);
        assertThat(attachments.downloadForCompany("other", companyId, id, uploaded.id()).content()).isEqualTo(pdf().getBytes());
        assertThatThrownBy(() -> attachments.downloadForCompany("other", -1L, id, uploaded.id())).isInstanceOf(ResponseStatusException.class);
        application.changeStatus(ApplicationStatus.WITHDRAWN);
        entityManager.flush();
        entityManager.clear();
        assertThat(resumeRepository.findVisibleToCompanyById(companyId, id)).isEmpty();
        assertThat(resumeRepository.findVisibleToCompany(companyId, page).getContent()).isEmpty();
        assertThat(resumeRepository.findById(id).orElseThrow().getVisibility()).isEqualTo(ResumeVisibility.PRIVATE);
        assertThatThrownBy(() -> attachments.downloadForCompany("other", companyId, id, uploaded.id())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void attachmentPersistenceLimitsOwnerIsolationAndCascadeDeletion() throws Exception {
        var resume = service.create("owner", new ResumeUpsertRequest("첨부 이력서", objectMapper.readTree("{}")));
        var second = service.create("owner", new ResumeUpsertRequest("다른 이력서", objectMapper.readTree("{}")));
        entityManager.flush();
        var attachments = new ResumeAttachmentService(jdbc, currentMemberService, mock(CompanyResumeService.class));
        var uploaded = attachments.upload("owner", resume.id(), pdf());
        entityManager.clear();
        assertThat(attachments.list("owner", resume.id())).hasSize(1);
        assertThat(attachments.download("owner", resume.id(), uploaded.id()).content()).isEqualTo(pdf().getBytes());
        assertNotFound(() -> attachments.list("other", resume.id()));
        assertNotFound(() -> attachments.upload("other", resume.id(), pdf()));
        assertNotFound(() -> attachments.download("other", resume.id(), uploaded.id()));
        assertNotFound(() -> attachments.delete("other", resume.id(), uploaded.id()));
        assertNotFound(() -> attachments.download("owner", second.id(), uploaded.id()));
        for (int index = 1; index < 5; index++) attachments.upload("owner", resume.id(), pdf());
        assertThatThrownBy(() -> attachments.upload("owner", resume.id(), pdf())).isInstanceOf(ResponseStatusException.class);
        assertThat(attachments.list("owner", resume.id())).hasSize(5);
        attachments.delete("owner", resume.id(), uploaded.id());
        assertNotFound(() -> attachments.download("owner", resume.id(), uploaded.id()));
        attachments.upload("owner", resume.id(), pdf());
        service.delete("owner", resume.id());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resume_attachments WHERE resume_id = ?", Integer.class, resume.id())).isZero();
    }

    private org.springframework.mock.web.MockMultipartFile pdf() {
        return new org.springframework.mock.web.MockMultipartFile("file", "portfolio.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
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
