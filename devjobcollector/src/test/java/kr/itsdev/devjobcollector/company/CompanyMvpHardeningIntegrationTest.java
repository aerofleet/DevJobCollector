package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberInvitationRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.hardening.SecurityAuditEventRepository;
import kr.itsdev.devjobcollector.security.hardening.SecurityAuditEventType;
import kr.itsdev.devjobcollector.security.hardening.SecurityHardeningProperties;
import kr.itsdev.devjobcollector.security.hardening.SecurityHardeningService;
import kr.itsdev.devjobcollector.security.hardening.SecurityRateLimitException;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Import({QuerydslConfig.class, CompanySignupFacade.class, CompanyAuthorizationService.class,
        CompanyMemberManagementService.class, CompanyVerificationService.class,
        CurrentMemberService.class, SecurityHardeningService.class,
        SecurityHardeningProperties.class, CompanyMvpHardeningIntegrationTest.MetricsConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompanyMvpHardeningIntegrationTest {
    private static final String EMAIL_PREFIX = "p602-company-";
    private static final int REQUESTS = 20;
    private static final int CONCURRENCY = 10;
    private static final long SIGNUP_P95_TARGET_MILLIS = 300L;

    @Autowired CompanySignupFacade signupFacade;
    @Autowired CompanyMemberManagementService memberService;
    @Autowired CompanyVerificationService verificationService;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMemberRepository memberRepository;
    @Autowired CompanyVerificationRequestRepository verificationRepository;
    @Autowired SecurityAuditEventRepository auditRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired PersonalProfileRepository profileRepository;
    @Autowired PlatformTransactionManager transactionManager;
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

    @AfterEach
    void cleanAfter() {
        jdbcTemplate.update("""
                DELETE FROM security_audit_events
                WHERE actor_user_id IN (SELECT id FROM users WHERE email LIKE ?)
                   OR subject_user_id IN (SELECT id FROM users WHERE email LIKE ?)
                """, EMAIL_PREFIX + "%", EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE r FROM company_verification_requests r
                JOIN users u ON u.id = r.requested_by
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE cm FROM company_members cm
                JOIN companies c ON c.id = cm.company_id
                JOIN users u ON u.id = c.created_by
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE c FROM companies c
                JOIN users u ON u.id = c.created_by
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE p FROM personal_profiles p
                JOIN users u ON u.id = p.user_id
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    @Test
    void concurrentDuplicateBusinessSignupCreatesOneCompanyOwnerAndAudit() throws Exception {
        List<UserAccount> owners = saveUsers("signup-owner", REQUESTS);
        String businessNumber = "901-23-45678";
        String hash = BusinessNumberProtection.protect(businessNumber).hash();

        List<String> outcomes = runConcurrently(REQUESTS, index -> () -> {
            try {
                signupFacade.signup(owners.get(index).getId().toString(),
                        signupRequest(businessNumber, "동시 가입"));
                return "SUCCESS";
            } catch (CompanyAlreadyExistsException exception) {
                return "DUPLICATE";
            } catch (SecurityRateLimitException exception) {
                return "RATE_LIMITED";
            }
        });

        List<Company> companies = companyRepository.findAll().stream()
                .filter(company -> hash.equals(company.getBusinessNumberHash()))
                .toList();
        assertThat(outcomes).hasSize(REQUESTS).containsOnly("SUCCESS", "DUPLICATE", "RATE_LIMITED");
        assertThat(Collections.frequency(outcomes, "SUCCESS")).isEqualTo(1);
        printOutcomes("COMPANY_SIGNUP_CONCURRENCY", outcomes);
        assertThat(companies).hasSize(1);
        assertThat(memberRepository.countByCompany_Id(companies.getFirst().getId())).isEqualTo(1);
        assertThat(auditCount(companies.getFirst().getId(), SecurityAuditEventType.COMPANY_CREATED))
                .isEqualTo(1);
    }

    @Test
    void concurrentDuplicateInvitationCreatesOneMembershipAndAudit() throws Exception {
        Fixture fixture = saveVerifiedCompany("invite");
        UserAccount invitee = saveUsers("invitee", 1).getFirst();
        CompanyMemberInvitationRequest request = new CompanyMemberInvitationRequest(
                invitee.getEmail(), CompanyMemberRole.VIEWER);

        List<String> outcomes = runConcurrently(REQUESTS, ignored -> () -> {
            try {
                memberService.invite(fixture.owner().getId().toString(),
                        fixture.company().getId(), request);
                return "SUCCESS";
            } catch (CompanyMemberManagementException exception) {
                return exception.getMessage();
            }
        });

        assertThat(outcomes).hasSize(REQUESTS)
                .containsOnly("SUCCESS", "COMPANY_MEMBER_ALREADY_EXISTS");
        assertThat(Collections.frequency(outcomes, "SUCCESS")).isEqualTo(1);
        printOutcomes("COMPANY_INVITATION_CONCURRENCY", outcomes);
        assertThat(memberRepository.findByCompany_IdAndUser_Id(
                fixture.company().getId(), invitee.getId())).isPresent();
        assertThat(memberRepository.countByCompany_Id(fixture.company().getId())).isEqualTo(2);
        assertThat(auditCount(fixture.company().getId(),
                SecurityAuditEventType.COMPANY_MEMBER_INVITED)).isEqualTo(1);
    }

    @Test
    void concurrentVerificationRequestsCreateOnePendingRequestAndAudit() throws Exception {
        Fixture fixture = saveVerifiedCompany("verification");
        inTransaction(() -> companyRepository.findById(fixture.company().getId())
                .orElseThrow()
                .changeStatus(CompanyStatus.PENDING_VERIFICATION));
        CompanyVerificationSubmitRequest request = new CompanyVerificationSubmitRequest(
                CompanyVerificationMethod.BUSINESS_REGISTRATION_DOCUMENT,
                "company-verification/p602/evidence.pdf");

        List<String> outcomes = runConcurrently(REQUESTS, ignored -> () -> {
            try {
                verificationService.submit(fixture.owner().getId().toString(),
                        fixture.company().getId(), request);
                return "SUCCESS";
            } catch (CompanyVerificationException exception) {
                return exception.getMessage();
            } catch (SecurityRateLimitException exception) {
                return "RATE_LIMITED";
            }
        });

        assertThat(outcomes).hasSize(REQUESTS).containsOnly(
                "SUCCESS", "COMPANY_VERIFICATION_PENDING_EXISTS", "RATE_LIMITED");
        assertThat(Collections.frequency(outcomes, "SUCCESS")).isEqualTo(1);
        printOutcomes("COMPANY_VERIFICATION_CONCURRENCY", outcomes);
        assertThat(verificationRepository.findAll().stream()
                .filter(item -> item.getCompany().getId().equals(fixture.company().getId())))
                .singleElement()
                .satisfies(item -> assertThat(item.getStatus())
                        .isEqualTo(CompanyVerificationStatus.PENDING));
        assertThat(auditCount(fixture.company().getId(),
                SecurityAuditEventType.COMPANY_VERIFICATION_REQUESTED)).isEqualTo(1);
    }

    @Test
    void uniqueCompanySignupDbSegmentMeetsP95TargetWithZeroErrors() {
        int warmups = 3;
        int measured = 30;
        List<UserAccount> owners = saveUsers("performance-owner", warmups + measured);
        List<Long> durations = new ArrayList<>(measured);

        for (int index = 0; index < warmups + measured; index++) {
            String businessNumber = "%010d".formatted(910_000_000L + index);
            long startedAt = System.nanoTime();
            signupFacade.signup(owners.get(index).getId().toString(),
                    signupRequest(businessNumber, "성능 " + index));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            if (index >= warmups) {
                durations.add(elapsedMillis);
            }
        }

        List<Long> sorted = durations.stream().sorted().toList();
        long p50 = percentile(sorted, 0.50);
        long p95 = percentile(sorted, 0.95);
        System.out.printf("COMPANY_SIGNUP_DB_PERFORMANCE count=%d p50_ms=%d p95_ms=%d errors=0%n",
                measured, p50, p95);

        assertThat(durations).hasSize(measured);
        assertThat(p95).isLessThanOrEqualTo(SIGNUP_P95_TARGET_MILLIS);
    }

    private List<UserAccount> saveUsers(String key, int count) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            List<UserAccount> users = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                UserAccount user = userRepository.save(UserAccount.activeSocial(
                        EMAIL_PREFIX + key + "-" + index + "@example.com",
                        key + "-" + index, AuthProvider.GITHUB,
                        EMAIL_PREFIX + key + "-subject-" + index));
                profileRepository.save(PersonalProfile.active(user));
                users.add(user);
            }
            userRepository.flush();
            profileRepository.flush();
            return users;
        });
    }

    private Fixture saveVerifiedCompany(String key) {
        UserAccount owner = saveUsers(key + "-owner", 1).getFirst();
        return new TransactionTemplate(transactionManager).execute(status -> {
            Company company = companyRepository.save(Company.pendingVerification(
                    "P6-02 " + key + " 주식회사", "P6-02 " + key,
                    BusinessNumberProtection.protect(numberFor(key)).hash(),
                    "***-**-12345", null, owner));
            company.changeStatus(CompanyStatus.VERIFIED);
            memberRepository.save(CompanyMember.activeOwner(
                    company, owner, LocalDateTime.of(2026, 9, 11, 12, 0)));
            return new Fixture(owner, company);
        });
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private CompanySignupRequest signupRequest(String businessNumber, String displayName) {
        return new CompanySignupRequest(
                displayName + " 주식회사", displayName, businessNumber, "https://example.com");
    }

    private String numberFor(String key) {
        long suffix = Integer.toUnsignedLong(key.hashCode()) % 10_000_000_000L;
        return "%010d".formatted(suffix);
    }

    private long auditCount(Long companyId, SecurityAuditEventType eventType) {
        return auditRepository.findAllByCompanyIdOrderByOccurredAtAscIdAsc(companyId).stream()
                .filter(event -> event.getEventType() == eventType)
                .count();
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private void printOutcomes(String metric, List<String> outcomes) {
        System.out.printf("%s requests=%d concurrency=%d success=%d duplicate=%d "
                        + "pending_exists=%d rate_limited=%d%n",
                metric, REQUESTS, CONCURRENCY,
                Collections.frequency(outcomes, "SUCCESS"),
                Collections.frequency(outcomes, "DUPLICATE")
                        + Collections.frequency(outcomes, "COMPANY_MEMBER_ALREADY_EXISTS"),
                Collections.frequency(outcomes, "COMPANY_VERIFICATION_PENDING_EXISTS"),
                Collections.frequency(outcomes, "RATE_LIMITED"));
    }

    private <T> List<T> runConcurrently(int requests,
                                        IntFunction<java.util.concurrent.Callable<T>> taskFactory)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                var task = taskFactory.apply(index);
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record Fixture(UserAccount owner, Company company) {}

    @TestConfiguration
    static class MetricsConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
