package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
@Import({QuerydslConfig.class, CompanyVerificationService.class, CurrentMemberService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompanyVerificationIntegrationTest {
    private static final String EMAIL_PREFIX = "p401-verification-";

    @Autowired CompanyVerificationService service;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMemberRepository memberRepository;
    @Autowired CompanyVerificationRequestRepository requestRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired PersonalProfileRepository profileRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;
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

    @BeforeEach
    void cleanBefore() { cleanup(); }

    @AfterEach
    void cleanAfter() { cleanup(); }

    @Test
    void persistsRequestAndApprovesItAtomically() {
        Fixture fixture = createFixture("approve");

        var submitted = service.submit(fixture.owner().getId().toString(), fixture.company().getId(), request());
        var reviewed = service.approve(fixture.admin().getId().toString(), submitted.requestId());

        assertThat(reviewed.requestStatus()).isEqualTo(CompanyVerificationStatus.APPROVED);
        assertThat(reviewed.companyStatus()).isEqualTo(CompanyStatus.VERIFIED);
        CompanyVerificationRequest stored = requestRepository.findById(submitted.requestId()).orElseThrow();
        assertThat(stored.getReviewedBy().getId()).isEqualTo(fixture.admin().getId());
        assertThat(stored.getReviewedAt()).isNotNull();
        assertThat(stored.getRejectionReason()).isNull();
    }

    @Test
    void rejectsThenAllowsOwnerToResubmit() {
        Fixture fixture = createFixture("reject");
        var first = service.submit(fixture.owner().getId().toString(), fixture.company().getId(), request());

        var rejected = service.reject(
                fixture.admin().getId().toString(), first.requestId(), "사업자등록증 식별 불가");
        var second = service.submit(fixture.owner().getId().toString(), fixture.company().getId(), request());

        assertThat(rejected.requestStatus()).isEqualTo(CompanyVerificationStatus.REJECTED);
        assertThat(second.requestStatus()).isEqualTo(CompanyVerificationStatus.PENDING);
        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(companyRepository.findById(fixture.company().getId()).orElseThrow().getStatus())
                .isEqualTo(CompanyStatus.PENDING_VERIFICATION);
    }

    @Test
    void preventsSecondPendingRequest() {
        Fixture fixture = createFixture("duplicate");
        service.submit(fixture.owner().getId().toString(), fixture.company().getId(), request());

        assertThatThrownBy(() -> service.submit(
                fixture.owner().getId().toString(), fixture.company().getId(), request()))
                .isInstanceOf(CompanyVerificationException.class)
                .hasMessage("COMPANY_VERIFICATION_PENDING_EXISTS");
    }

    private Fixture createFixture(String key) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UserAccount owner = saveMember(key + "-owner");
            UserAccount admin = saveMember(key + "-admin");
            userRepository.flush();
            profileRepository.flush();
            jdbcTemplate.update("UPDATE users SET role = 'PLATFORM_ADMIN' WHERE id = ?", admin.getId());
            entityManager.clear();
            Company company = companyRepository.save(Company.pendingVerification(
                    "P4-01 테스트 주식회사", "P4-01 테스트", hashFor(key), "***-**-12345", null, owner));
            memberRepository.save(CompanyMember.activeOwner(company, owner, java.time.LocalDateTime.now()));
            return new Fixture(owner, admin, company);
        });
    }

    private UserAccount saveMember(String key) {
        UserAccount user = userRepository.save(UserAccount.activeSocial(
                EMAIL_PREFIX + key + "@example.com", key, AuthProvider.GITHUB,
                EMAIL_PREFIX + "subject-" + key));
        profileRepository.save(PersonalProfile.active(user));
        return user;
    }

    private CompanyVerificationSubmitRequest request() {
        return new CompanyVerificationSubmitRequest(
                CompanyVerificationMethod.BUSINESS_REGISTRATION_DOCUMENT,
                "company-verification/test/evidence.pdf");
    }

    private String hashFor(String key) {
        long suffix = Integer.toUnsignedLong(key.hashCode()) % 10_000_000_000L;
        return BusinessNumberProtection.protect("%010d".formatted(suffix)).hash();
    }

    private void cleanup() {
        jdbcTemplate.update("""
                DELETE r FROM company_verification_requests r
                JOIN users u ON u.id = r.requested_by
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE cm FROM company_members cm
                JOIN users u ON u.id = cm.user_id
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

    private record Fixture(UserAccount owner, UserAccount admin, Company company) {}
}
