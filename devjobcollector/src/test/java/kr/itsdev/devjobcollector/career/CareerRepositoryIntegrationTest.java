package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
class CareerRepositoryIntegrationTest {
    @Autowired CareerResumeRepository resumeRepository;
    @Autowired JobBookmarkRepository bookmarkRepository;
    @Autowired JobViewHistoryRepository viewHistoryRepository;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired JobPostRepository jobPostRepository;
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
    void persistsAndQueriesAllCareerRecordsByOwner() {
        UserAccount user = saveUser("owner@example.com");
        JobPost jobPost = saveJob("owner-job");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 25, 10, 0);

        CareerResume resume = resumeRepository.save(CareerResume.draft(
                user, "기본 이력서", "{\"summary\":\"backend\"}"));
        JobBookmark bookmark = bookmarkRepository.save(JobBookmark.create(user, jobPost));
        JobViewHistory history = viewHistoryRepository.save(
                JobViewHistory.firstView(user, jobPost, occurredAt));
        JobApplication application = applicationRepository.save(
                JobApplication.applied(user, jobPost, occurredAt, "서류 제출"));
        entityManager.flush();
        entityManager.clear();

        assertThat(resumeRepository.findByIdAndUser_Id(resume.getId(), user.getId())).isPresent();
        assertThat(bookmarkRepository.existsByUser_IdAndJobPost_Id(user.getId(), jobPost.getId())).isTrue();
        assertThat(viewHistoryRepository.findByUser_IdAndJobPost_Id(user.getId(), jobPost.getId()))
                .get().extracting(JobViewHistory::getViewCount).isEqualTo(1);
        assertThat(applicationRepository.findByIdAndUser_Id(application.getId(), user.getId()))
                .get().extracting(JobApplication::getStatus).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(bookmark.getId()).isNotNull();
        assertThat(history.getId()).isNotNull();
    }

    @Test
    void ownerQueriesNeverReturnAnotherUsersRecords() {
        UserAccount owner = saveUser("owner-only@example.com");
        UserAccount other = saveUser("other@example.com");
        JobPost jobPost = saveJob("owner-only-job");
        CareerResume resume = resumeRepository.saveAndFlush(CareerResume.draft(owner, "소유 이력서", "{}"));
        applicationRepository.saveAndFlush(JobApplication.applied(
                owner, jobPost, LocalDateTime.of(2026, 8, 25, 11, 0), null));
        entityManager.clear();

        assertThat(resumeRepository.findByIdAndUser_Id(resume.getId(), other.getId())).isEmpty();
        assertThat(resumeRepository.findAllByUser_IdOrderByUpdatedAtDescIdDesc(other.getId())).isEmpty();
        assertThat(applicationRepository.findAllByUser_IdOrderByUpdatedAtDescIdDesc(other.getId())).isEmpty();
    }

    @Test
    void rejectsDuplicateBookmarkForSameOwnerAndJob() {
        UserAccount user = saveUser("duplicate-bookmark@example.com");
        JobPost jobPost = saveJob("duplicate-bookmark-job");
        bookmarkRepository.saveAndFlush(JobBookmark.create(user, jobPost));

        assertThatThrownBy(() -> bookmarkRepository.saveAndFlush(JobBookmark.create(user, jobPost)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotentInsertKeepsOneBookmarkForSameOwnerAndJob() {
        UserAccount user = saveUser("idempotent-bookmark@example.com");
        JobPost jobPost = saveJob("idempotent-bookmark-job");
        entityManager.flush();

        bookmarkRepository.insertIfAbsent(user.getId(), jobPost.getId());
        bookmarkRepository.insertIfAbsent(user.getId(), jobPost.getId());

        assertThat(bookmarkRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(user.getId()))
                .hasSize(1)
                .first()
                .extracting(bookmark -> bookmark.getJobPost().getId())
                .isEqualTo(jobPost.getId());
    }

    private UserAccount saveUser(String email) {
        return userRepository.save(UserAccount.activeSocial(
                email, email, AuthProvider.GITHUB, "subject-" + email));
    }

    private JobPost saveJob(String originalSn) {
        return jobPostRepository.save(JobPost.builder()
                .sourcePlatform(SourcePlatform.SARAMIN)
                .originalSn(originalSn)
                .companyName("테스트 기업")
                .title("백엔드 개발자")
                .startDate(LocalDate.of(2026, 8, 25))
                .endDate(LocalDate.of(2026, 9, 25))
                .originalUrl("https://example.com/jobs/" + originalSn)
                .build());
    }
}
