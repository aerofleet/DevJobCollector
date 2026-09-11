package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import kr.itsdev.devjobcollector.security.hardening.SecurityHardeningService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
@Import({QuerydslConfig.class, CompanySignupFacade.class, CurrentMemberService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompanySignupTransactionIntegrationTest {
    @MockitoBean SecurityHardeningService hardeningService;
    private static final String TEST_EMAIL_PREFIX = "p303-company-";
    private static final String REJECT_TRIGGER = "reject_p303_company_membership";

    @Autowired CompanySignupFacade signupFacade;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMemberRepository memberRepository;
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

    @BeforeEach
    void cleanBeforeTest() {
        cleanupFixtures();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanupFixtures();
    }

    @Test
    void atomicallyCreatesPendingCompanyAndActiveOwnerMembership() {
        UserAccount owner = saveActiveMember("success");

        var response = signupFacade.signup(owner.getId().toString(), request("111-22-33333"));

        Company company = companyRepository.findById(response.companyId()).orElseThrow();
        CompanyMember member = memberRepository
                .findByCompany_IdAndUser_Id(company.getId(), owner.getId()).orElseThrow();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
        assertThat(company.getBusinessNumberHash()).hasSize(64).doesNotContain("1112233333");
        assertThat(company.getBusinessNumberMasked()).isEqualTo("***-**-33333");
        assertThat(member.isActiveOwner()).isTrue();
        assertThat(member.getJoinedAt()).isNotNull();
    }

    @Test
    void duplicateBusinessNumberCreatesNoAdditionalCompanyOrMembership() {
        UserAccount firstOwner = saveActiveMember("duplicate-1");
        UserAccount secondOwner = saveActiveMember("duplicate-2");
        CompanySignupRequest request = request("222-33-44444");
        var firstSignup = signupFacade.signup(firstOwner.getId().toString(), request);

        assertThatThrownBy(() -> signupFacade.signup(secondOwner.getId().toString(), request))
                .isInstanceOf(CompanyAlreadyExistsException.class);

        String hash = BusinessNumberProtection.protect(request.businessNumber()).hash();
        assertThat(companyRepository.existsByBusinessNumberHash(hash)).isTrue();
        assertThat(memberRepository.countByCompany_Id(firstSignup.companyId())).isEqualTo(1);
    }

    @Test
    void membershipInsertFailureRollsBackCompanyInsert() {
        UserAccount owner = saveActiveMember("rollback");
        String hash = BusinessNumberProtection.protect("333-44-55555").hash();
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_p303_company_membership
                BEFORE INSERT ON company_members
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced membership failure'
                """);

        assertThatThrownBy(() -> signupFacade.signup(
                owner.getId().toString(), request("333-44-55555")))
                .isInstanceOf(DataAccessException.class);

        assertThat(companyRepository.existsByBusinessNumberHash(hash)).isFalse();
    }

    private UserAccount saveActiveMember(String key) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UserAccount user = userRepository.save(UserAccount.activeSocial(
                    TEST_EMAIL_PREFIX + key + "@example.com", key, AuthProvider.GITHUB,
                    TEST_EMAIL_PREFIX + "subject-" + key));
            profileRepository.save(PersonalProfile.active(user));
            return user;
        });
    }

    private CompanySignupRequest request(String businessNumber) {
        return new CompanySignupRequest(
                "P3-03 테스트 주식회사", "P3-03 테스트", businessNumber, "https://example.com");
    }

    private void cleanupFixtures() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + REJECT_TRIGGER);
        jdbcTemplate.update("""
                DELETE cm FROM company_members cm
                JOIN companies c ON c.id = cm.company_id
                JOIN users u ON u.id = c.created_by
                WHERE u.email LIKE ?
                """, TEST_EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE c FROM companies c
                JOIN users u ON u.id = c.created_by
                WHERE u.email LIKE ?
                """, TEST_EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE p FROM personal_profiles p
                JOIN users u ON u.id = p.user_id
                WHERE u.email LIKE ?
                """, TEST_EMAIL_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", TEST_EMAIL_PREFIX + "%");
    }
}
