package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import org.junit.jupiter.api.Test;

class CompanyDomainTest {
    private static final String BUSINESS_NUMBER_HASH = "A".repeat(64);

    @Test
    void createsPendingCompanyWithoutBusinessNumberPlaintext() {
        UserAccount creator = user("creator@example.com");

        Company company = Company.pendingVerification(
                " 테스트 주식회사 ", " 테스트 ", BUSINESS_NUMBER_HASH,
                "***-**-12345", " ", creator);

        assertThat(company.getLegalName()).isEqualTo("테스트 주식회사");
        assertThat(company.getDisplayName()).isEqualTo("테스트");
        assertThat(company.getBusinessNumberHash()).isEqualTo("a".repeat(64));
        assertThat(company.getBusinessNumberMasked()).isEqualTo("***-**-12345");
        assertThat(company.getWebsiteUrl()).isNull();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
    }

    @Test
    void rejectsValueThatIsNotSha256Hex() {
        assertThatIllegalArgumentException().isThrownBy(() -> Company.pendingVerification(
                "테스트 주식회사", "테스트", "123-45-67890",
                "***-**-67890", null, user("invalid-hash@example.com")));
    }

    @Test
    void createsActiveOwnerAndInvitedMemberWithSeparateRoleAndStatus() {
        Company company = company("owner@example.com");
        UserAccount owner = company.getCreatedBy();
        UserAccount invitee = user("invitee@example.com");
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 7, 10, 0);

        CompanyMember activeOwner = CompanyMember.activeOwner(company, owner, joinedAt);
        CompanyMember invitedAdmin = CompanyMember.invited(
                company, invitee, CompanyMemberRole.ADMIN, owner);

        assertThat(activeOwner.isActiveOwner()).isTrue();
        assertThat(activeOwner.getJoinedAt()).isEqualTo(joinedAt);
        assertThat(invitedAdmin.getRole()).isEqualTo(CompanyMemberRole.ADMIN);
        assertThat(invitedAdmin.getStatus()).isEqualTo(CompanyMemberStatus.INVITED);
        assertThat(invitedAdmin.getJoinedAt()).isNull();
        assertThat(invitedAdmin.getInvitedBy()).isSameAs(owner);
    }

    @Test
    void recordsFirstJoinTimeWhenInvitationBecomesActive() {
        Company company = company("inviter@example.com");
        CompanyMember member = CompanyMember.invited(
                company, user("member@example.com"), CompanyMemberRole.RECRUITER,
                company.getCreatedBy());
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 7, 11, 0);

        member.changeStatus(CompanyMemberStatus.ACTIVE, joinedAt);
        member.changeStatus(CompanyMemberStatus.SUSPENDED, joinedAt.plusDays(1));
        member.changeStatus(CompanyMemberStatus.ACTIVE, joinedAt.plusDays(2));

        assertThat(member.getStatus()).isEqualTo(CompanyMemberStatus.ACTIVE);
        assertThat(member.getJoinedAt()).isEqualTo(joinedAt);
    }

    private Company company(String email) {
        return Company.pendingVerification("테스트 주식회사", "테스트", BUSINESS_NUMBER_HASH,
                "***-**-12345", "https://example.com", user(email));
    }

    private UserAccount user(String email) {
        return UserAccount.activeSocial(email, email, AuthProvider.GITHUB, "subject-" + email);
    }
}
