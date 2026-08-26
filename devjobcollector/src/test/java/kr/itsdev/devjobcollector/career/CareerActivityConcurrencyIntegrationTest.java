package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.dto.career.JobApplicationCreateRequest;
import kr.itsdev.devjobcollector.dto.career.JobApplicationStatusUpdateRequest;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({
        QuerydslConfig.class,
        JobBookmarkService.class,
        JobApplicationService.class,
        JobViewHistoryService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CareerActivityConcurrencyIntegrationTest {
    private static final int REQUESTS = 20;
    private static final int CONCURRENCY = 10;

    @Autowired JobBookmarkService bookmarkService;
    @Autowired JobApplicationService applicationService;
    @Autowired JobViewHistoryService viewHistoryService;
    @Autowired JobBookmarkRepository bookmarkRepository;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired JobViewHistoryRepository viewHistoryRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired JobPostRepository jobPostRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean CurrentMemberService currentMemberService;

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
    void keepsOneBookmarkAndApplicationForTwentyConcurrentCreates() throws Exception {
        Fixture fixture = saveFixture("concurrent-create");
        when(currentMemberService.requireCurrentMember("owner-create")).thenReturn(fixture.user());

        runConcurrently(() -> bookmarkService.create("owner-create", fixture.jobPost().getId()));
        runConcurrently(() -> applicationService.create(
                "owner-create", fixture.jobPost().getId(), new JobApplicationCreateRequest("동시 지원")));

        assertThat(bookmarkRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(fixture.user().getId()))
                .hasSize(1);
        assertThat(applicationRepository.findAllByUser_IdOrderByUpdatedAtDescIdDesc(fixture.user().getId()))
                .hasSize(1)
                .first()
                .extracting(JobApplication::getStatus)
                .isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void countsTwentyConcurrentViewsWithoutDuplicateRows() throws Exception {
        Fixture fixture = saveFixture("concurrent-view");
        when(currentMemberService.requireCurrentMember("owner-view")).thenReturn(fixture.user());

        runConcurrently(() -> viewHistoryService.record("owner-view", fixture.jobPost().getId()));

        List<JobViewHistory> rows = viewHistoryRepository
                .findTop100ByUser_IdOrderByLastViewedAtDescIdDesc(fixture.user().getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getViewCount()).isEqualTo(REQUESTS);
    }

    @Test
    void neverExposesOrMutatesAnotherMembersCareerActivities() {
        Fixture owner = saveFixture("ownership-owner");
        UserAccount other = saveUser("ownership-other@example.com");
        when(currentMemberService.requireCurrentMember("owner")).thenReturn(owner.user());
        when(currentMemberService.requireCurrentMember("other")).thenReturn(other);

        var bookmark = bookmarkService.create("owner", owner.jobPost().getId());
        var application = applicationService.create(
                "owner", owner.jobPost().getId(), new JobApplicationCreateRequest("소유자 메모"));
        viewHistoryService.record("owner", owner.jobPost().getId());

        assertThat(bookmarkService.list("other")).isEmpty();
        assertThat(applicationService.list("other")).isEmpty();
        assertThat(viewHistoryService.list("other")).isEmpty();

        bookmarkService.delete("other", bookmark.jobPostId());
        assertThat(bookmarkRepository.existsByUser_IdAndJobPost_Id(
                owner.user().getId(), owner.jobPost().getId())).isTrue();
        assertThatThrownBy(() -> applicationService.changeStatus(
                "other", application.applicationId(),
                new JobApplicationStatusUpdateRequest(ApplicationStatus.WITHDRAWN)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(applicationRepository.findByIdAndUser_Id(
                application.applicationId(), owner.user().getId()))
                .get()
                .extracting(JobApplication::getStatus)
                .isEqualTo(ApplicationStatus.APPLIED);
    }

    private void runConcurrently(ThrowingAction action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < REQUESTS; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    action.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Fixture saveFixture(String key) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            UserAccount user = userRepository.save(UserAccount.activeSocial(
                    key + "@example.com", key + "@example.com", AuthProvider.GITHUB, "subject-" + key));
            JobPost jobPost = jobPostRepository.save(JobPost.builder()
                    .sourcePlatform(SourcePlatform.SARAMIN)
                    .originalSn(key)
                    .companyName("테스트 기업")
                    .title("백엔드 개발자")
                    .startDate(LocalDate.of(2026, 8, 26))
                    .endDate(LocalDate.of(2026, 9, 26))
                    .originalUrl("https://example.com/jobs/" + key)
                    .build());
            return new Fixture(user, jobPost);
        });
    }

    private UserAccount saveUser(String email) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> userRepository.save(UserAccount.activeSocial(
                email, email, AuthProvider.GITHUB, "subject-" + email)));
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private record Fixture(UserAccount user, JobPost jobPost) {
    }
}
