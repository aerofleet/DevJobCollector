package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, CompanyMembershipService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompanyMembershipConcurrencyIntegrationTest {
    @Autowired CompanyMembershipService membershipService;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMemberRepository memberRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired PlatformTransactionManager transactionManager;

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
    void concurrentOwnerDemotionsNeverLeaveCompanyWithoutActiveOwner() throws Exception {
        Fixture fixture = saveFixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> demoteAfter(start, fixture.companyId(), fixture.firstUserId()));
            Future<Boolean> second = executor.submit(() -> demoteAfter(start, fixture.companyId(), fixture.secondUserId()));

            start.countDown();
            List<Boolean> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(memberRepository.countByCompany_IdAndRoleAndStatus(
                    fixture.companyId(), CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE))
                    .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean demoteAfter(CountDownLatch start, Long companyId, Long userId) throws InterruptedException {
        start.await();
        try {
            membershipService.changeRole(companyId, userId, CompanyMemberRole.ADMIN);
            return true;
        } catch (LastActiveOwnerException expected) {
            return false;
        }
    }

    private Fixture saveFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UserAccount first = userRepository.save(UserAccount.activeSocial(
                    "concurrent-owner-1@example.com", "owner-1", AuthProvider.GITHUB,
                    "company-owner-subject-1"));
            UserAccount second = userRepository.save(UserAccount.activeSocial(
                    "concurrent-owner-2@example.com", "owner-2", AuthProvider.GITHUB,
                    "company-owner-subject-2"));
            Company company = companyRepository.save(Company.pendingVerification(
                    "동시성 테스트 주식회사", "동시성 테스트", "c".repeat(64),
                    "***-**-54321", null, first));
            LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 7, 12, 0);
            memberRepository.save(CompanyMember.activeOwner(company, first, joinedAt));
            memberRepository.save(CompanyMember.activeOwner(company, second, joinedAt));
            return new Fixture(company.getId(), first.getId(), second.getId());
        });
    }

    private record Fixture(Long companyId, Long firstUserId, Long secondUserId) {
    }
}
