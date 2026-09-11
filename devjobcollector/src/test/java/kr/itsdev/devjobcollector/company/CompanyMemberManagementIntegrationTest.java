package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberInvitationRequest;
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
@Import({QuerydslConfig.class, CompanyAuthorizationService.class,
        CompanyMemberManagementService.class, CurrentMemberService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompanyMemberManagementIntegrationTest {
    @MockitoBean SecurityHardeningService hardeningService;
    private static final String EMAIL_PREFIX = "p403-member-";

    @Autowired CompanyMemberManagementService service;
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
    void cleanBefore() { cleanup(); }

    @AfterEach
    void cleanAfter() { cleanup(); }

    @Test
    void ownerInvitesMemberAndDuplicateInvitationIsRejected() {
        Fixture fixture = createFixture("invite", CompanyStatus.VERIFIED, true);
        UserAccount invitee = saveMember("invite-target");

        var response = service.invite(
                fixture.owner().getId().toString(), fixture.company().getId(),
                invitation(invitee, CompanyMemberRole.ADMIN));

        assertThat(response.role()).isEqualTo(CompanyMemberRole.ADMIN);
        assertThat(response.status()).isEqualTo(CompanyMemberStatus.INVITED);
        assertThat(response.joinedAt()).isNull();
        assertThat(memberRepository.countByCompany_Id(fixture.company().getId())).isEqualTo(3L);
        assertThatThrownBy(() -> service.invite(
                fixture.owner().getId().toString(), fixture.company().getId(),
                invitation(invitee, CompanyMemberRole.VIEWER)))
                .isInstanceOf(CompanyMemberManagementException.class)
                .hasMessage("COMPANY_MEMBER_ALREADY_EXISTS");
    }

    @Test
    void adminInvitesRecruiterButCannotAssignAdmin() {
        Fixture fixture = createFixture("admin", CompanyStatus.VERIFIED, true);
        UserAccount recruiter = saveMember("admin-recruiter");
        UserAccount nextAdmin = saveMember("admin-next-admin");

        var invited = service.invite(
                fixture.admin().getId().toString(), fixture.company().getId(),
                invitation(recruiter, CompanyMemberRole.RECRUITER));

        assertThat(invited.role()).isEqualTo(CompanyMemberRole.RECRUITER);
        assertThatThrownBy(() -> service.invite(
                fixture.admin().getId().toString(), fixture.company().getId(),
                invitation(nextAdmin, CompanyMemberRole.ADMIN)))
                .isInstanceOf(CompanyAuthorizationException.class)
                .hasMessage("COMPANY_ACCESS_DENIED");
    }

    @Test
    void ownerCanChangeAdminRoleWhileAdminCannotChangeOwner() {
        Fixture fixture = createFixture("roles", CompanyStatus.VERIFIED, true);

        assertThatThrownBy(() -> service.changeRole(
                fixture.admin().getId().toString(), fixture.company().getId(),
                fixture.ownerMembershipId(), CompanyMemberRole.RECRUITER))
                .isInstanceOf(CompanyAuthorizationException.class)
                .hasMessage("COMPANY_ACCESS_DENIED");

        var changed = service.changeRole(
                fixture.owner().getId().toString(), fixture.company().getId(),
                fixture.adminMembershipId(), CompanyMemberRole.RECRUITER);
        assertThat(changed.role()).isEqualTo(CompanyMemberRole.RECRUITER);
    }

    @Test
    void removeThenReinviteReusesMembershipAndListExcludesLeftState() {
        Fixture fixture = createFixture("reinvite", CompanyStatus.VERIFIED, true);
        UserAccount member = saveMember("reinvite-target");
        Long memberId = saveActiveMember(
                fixture.company(), member, CompanyMemberRole.VIEWER, fixture.owner()).getId();

        service.remove(fixture.owner().getId().toString(), fixture.company().getId(), memberId);
        assertThat(service.listMembers(
                fixture.owner().getId().toString(), fixture.company().getId()))
                .noneMatch(response -> response.memberId().equals(memberId));

        var reinvited = service.invite(
                fixture.owner().getId().toString(), fixture.company().getId(),
                invitation(member, CompanyMemberRole.RECRUITER));

        assertThat(reinvited.memberId()).isEqualTo(memberId);
        assertThat(reinvited.status()).isEqualTo(CompanyMemberStatus.INVITED);
        assertThat(reinvited.role()).isEqualTo(CompanyMemberRole.RECRUITER);
        assertThat(memberRepository.countByCompany_Id(fixture.company().getId())).isEqualTo(3L);
    }

    @Test
    void lastActiveOwnerRemovalIsRejected() {
        Fixture fixture = createFixture("last-owner", CompanyStatus.VERIFIED, false);

        assertThatThrownBy(() -> service.remove(
                fixture.owner().getId().toString(), fixture.company().getId(),
                fixture.ownerMembershipId()))
                .isInstanceOf(LastActiveOwnerException.class);

        assertThat(memberRepository.findById(fixture.ownerMembershipId()).orElseThrow().getStatus())
                .isEqualTo(CompanyMemberStatus.ACTIVE);
    }

    @Test
    void unverifiedCompanyCannotManageMembers() {
        Fixture fixture = createFixture("pending", CompanyStatus.PENDING_VERIFICATION, false);
        UserAccount invitee = saveMember("pending-target");

        assertThatThrownBy(() -> service.invite(
                fixture.owner().getId().toString(), fixture.company().getId(),
                invitation(invitee, CompanyMemberRole.VIEWER)))
                .isInstanceOf(CompanyAuthorizationException.class)
                .hasMessage("COMPANY_NOT_VERIFIED");
    }

    private Fixture createFixture(String key, CompanyStatus status, boolean withAdmin) {
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            UserAccount owner = saveMember(key + "-owner");
            UserAccount admin = withAdmin ? saveMember(key + "-admin") : null;
            Company company = companyRepository.save(Company.pendingVerification(
                    "P4-03 테스트 주식회사", "P4-03 테스트", hashFor(key),
                    "***-**-12345", null, owner));
            company.changeStatus(status);
            CompanyMember ownerMembership = memberRepository.save(CompanyMember.activeOwner(
                    company, owner, LocalDateTime.of(2026, 9, 9, 10, 0)));
            CompanyMember adminMembership = withAdmin
                    ? saveActiveMember(company, admin, CompanyMemberRole.ADMIN, owner) : null;
            return new Fixture(owner, admin, company, ownerMembership.getId(),
                    adminMembership == null ? null : adminMembership.getId());
        });
    }

    private CompanyMember saveActiveMember(Company company, UserAccount user,
                                           CompanyMemberRole role, UserAccount inviter) {
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            CompanyMember member = CompanyMember.invited(company, user, role, inviter);
            member.changeStatus(CompanyMemberStatus.ACTIVE, LocalDateTime.of(2026, 9, 9, 10, 0));
            return memberRepository.save(member);
        });
    }

    private UserAccount saveMember(String key) {
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            UserAccount user = userRepository.save(UserAccount.activeSocial(
                    EMAIL_PREFIX + key + "@example.com", key, AuthProvider.GITHUB,
                    EMAIL_PREFIX + "subject-" + key));
            profileRepository.save(PersonalProfile.active(user));
            return user;
        });
    }

    private CompanyMemberInvitationRequest invitation(UserAccount user, CompanyMemberRole role) {
        return new CompanyMemberInvitationRequest(user.getEmail(), role);
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

    private record Fixture(
            UserAccount owner,
            UserAccount admin,
            Company company,
            Long ownerMembershipId,
            Long adminMembershipId
    ) {}
}
