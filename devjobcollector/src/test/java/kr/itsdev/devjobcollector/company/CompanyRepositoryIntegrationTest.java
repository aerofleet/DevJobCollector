package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
@Import({QuerydslConfig.class, CompanyMembershipService.class, CompanyAuthorizationService.class})
class CompanyRepositoryIntegrationTest {
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMemberRepository memberRepository;
    @Autowired CompanyMembershipService membershipService;
    @Autowired CompanyAuthorizationService authorizationService;
    @Autowired UserAccountRepository userRepository;
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
    void persistsCompanyAndQueriesMembershipByCompanyAndUser() {
        UserAccount owner = saveUser("repository-owner@example.com");
        Company company = saveCompany(owner, "a".repeat(64));
        CompanyMember membership = memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, owner, LocalDateTime.of(2026, 9, 7, 10, 0)));
        entityManager.clear();

        CompanyMember found = memberRepository
                .findByCompany_IdAndUser_Id(company.getId(), owner.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(membership.getId());
        assertThat(found.isActiveOwner()).isTrue();
        assertThat(companyRepository.existsByBusinessNumberHash("a".repeat(64))).isTrue();
        assertThat(memberRepository.findAllByUser_IdAndStatusOrderByCompany_IdAsc(
                owner.getId(), CompanyMemberStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void rejectsDuplicateCompanyMembership() {
        UserAccount owner = saveUser("duplicate-member@example.com");
        Company company = saveCompany(owner, "b".repeat(64));
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 7, 10, 0);
        memberRepository.saveAndFlush(CompanyMember.activeOwner(company, owner, joinedAt));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                CompanyMember.activeOwner(company, owner, joinedAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesLastActiveOwnerInvariantAndAllowsTransferAfterSecondOwnerIsActive() {
        UserAccount firstOwner = saveUser("first-owner@example.com");
        UserAccount secondOwner = saveUser("second-owner@example.com");
        Company company = saveCompany(firstOwner, "d".repeat(64));
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 7, 10, 0);
        memberRepository.save(CompanyMember.activeOwner(company, firstOwner, joinedAt));
        memberRepository.flush();

        assertThatThrownBy(() -> membershipService.changeRole(
                company.getId(), firstOwner.getId(), CompanyMemberRole.ADMIN))
                .isInstanceOf(LastActiveOwnerException.class);

        memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, secondOwner, joinedAt.plusMinutes(1)));
        membershipService.changeRole(company.getId(), firstOwner.getId(), CompanyMemberRole.ADMIN);
        entityManager.flush();
        entityManager.clear();

        assertThat(memberRepository.findByCompany_IdAndUser_Id(company.getId(), firstOwner.getId()))
                .get()
                .extracting(CompanyMember::getRole)
                .isEqualTo(CompanyMemberRole.ADMIN);
        assertThat(memberRepository.countByCompany_IdAndRoleAndStatus(
                company.getId(), CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    void authorizesActiveMembershipForVerifiedCompany() {
        UserAccount owner = saveUser("authorized-owner@example.com");
        Company company = saveCompany(owner, "e".repeat(64));
        company.changeStatus(CompanyStatus.VERIFIED);
        companyRepository.save(company);
        CompanyMember membership = memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, owner, LocalDateTime.of(2026, 9, 8, 10, 0)));
        entityManager.clear();

        CompanyMember authorized = authorizationService.authorize(
                company.getId(), owner.getId(), CompanyPermission.EDIT_COMPANY);

        assertThat(authorized.getId()).isEqualTo(membership.getId());
    }

    @Test
    void rejectsActiveMembershipWhenCompanyIsNotVerified() {
        UserAccount owner = saveUser("pending-company-owner@example.com");
        Company company = saveCompany(owner, "f".repeat(64));
        memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, owner, LocalDateTime.of(2026, 9, 8, 10, 0)));
        entityManager.clear();

        assertThatThrownBy(() -> authorizationService.authorize(
                company.getId(), owner.getId(), CompanyPermission.EDIT_COMPANY))
                .isInstanceOf(CompanyAuthorizationException.class)
                .hasMessage("COMPANY_NOT_VERIFIED");
    }

    private UserAccount saveUser(String email) {
        return userRepository.save(UserAccount.activeSocial(
                email, email, AuthProvider.GITHUB, "subject-" + email));
    }

    private Company saveCompany(UserAccount creator, String hash) {
        return companyRepository.save(Company.pendingVerification(
                "테스트 주식회사", "테스트", hash, "***-**-12345",
                "https://example.com", creator));
    }
}
