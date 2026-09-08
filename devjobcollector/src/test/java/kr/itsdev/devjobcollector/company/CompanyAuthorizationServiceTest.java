package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyAuthorizationServiceTest {
    private static final Long COMPANY_ID = 1L;
    private static final Long USER_ID = 10L;
    private final CompanyMemberRepository memberRepository = mock(CompanyMemberRepository.class);
    private final CompanyAuthorizationService service = new CompanyAuthorizationService(memberRepository);

    @ParameterizedTest(name = "{0} / {1} / {2} => {3}")
    @MethodSource("authorizationMatrix")
    void enforcesRoleAndCompanyStatusMatrix(CompanyMemberRole role, CompanyStatus companyStatus,
                                            CompanyPermission permission, boolean allowed) {
        CompanyMember membership = activeMembership(role, companyStatus);
        when(memberRepository.findByCompany_IdAndUser_Id(COMPANY_ID, USER_ID))
                .thenReturn(Optional.of(membership));

        if (allowed) {
            assertThat(service.authorize(COMPANY_ID, USER_ID, permission)).isSameAs(membership);
        } else {
            String expectedCode = roleAllows(role, permission)
                    ? "COMPANY_NOT_VERIFIED" : "COMPANY_ACCESS_DENIED";
            assertThatThrownBy(() -> service.authorize(COMPANY_ID, USER_ID, permission))
                    .isInstanceOf(CompanyAuthorizationException.class)
                    .hasMessage(expectedCode);
        }
    }

    @ParameterizedTest
    @MethodSource("inactiveMembershipStatuses")
    void deniesInactiveMembershipBeforeEvaluatingCompanyState(CompanyMemberStatus membershipStatus) {
        CompanyMember membership = membership(
                CompanyMemberRole.OWNER, membershipStatus, CompanyStatus.PENDING_VERIFICATION);
        when(memberRepository.findByCompany_IdAndUser_Id(COMPANY_ID, USER_ID))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.authorize(
                COMPANY_ID, USER_ID, CompanyPermission.EDIT_COMPANY))
                .isInstanceOf(CompanyAuthorizationException.class)
                .hasMessage("COMPANY_ACCESS_DENIED");
    }

    @Test
    void deniesUserWithoutMembership() {
        when(memberRepository.findByCompany_IdAndUser_Id(COMPANY_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(
                COMPANY_ID, USER_ID, CompanyPermission.VIEW_COMPANY))
                .isInstanceOf(CompanyAuthorizationException.class)
                .hasMessage("COMPANY_ACCESS_DENIED");
    }

    private static Stream<Arguments> authorizationMatrix() {
        return Stream.of(CompanyMemberRole.values())
                .flatMap(role -> Stream.of(CompanyStatus.values())
                        .flatMap(status -> Stream.of(CompanyPermission.values())
                                .map(permission -> Arguments.of(
                                        role, status, permission,
                                        status == CompanyStatus.VERIFIED
                                                && roleAllows(role, permission)))));
    }

    private static Stream<CompanyMemberStatus> inactiveMembershipStatuses() {
        return Stream.of(CompanyMemberStatus.INVITED,
                CompanyMemberStatus.SUSPENDED, CompanyMemberStatus.LEFT);
    }

    private static boolean roleAllows(CompanyMemberRole role, CompanyPermission permission) {
        return switch (permission) {
            case VIEW_COMPANY, VIEW_MEMBERS -> true;
            case EDIT_COMPANY, INVITE_MEMBERS, ASSIGN_RECRUITER, REMOVE_MEMBER ->
                    role == CompanyMemberRole.OWNER || role == CompanyMemberRole.ADMIN;
            case ASSIGN_ADMIN, TRANSFER_OWNERSHIP -> role == CompanyMemberRole.OWNER;
            case CREATE_JOB_POST, EDIT_JOB_POST -> role != CompanyMemberRole.VIEWER;
        };
    }

    private CompanyMember activeMembership(CompanyMemberRole role, CompanyStatus companyStatus) {
        return membership(role, CompanyMemberStatus.ACTIVE, companyStatus);
    }

    private CompanyMember membership(CompanyMemberRole role, CompanyMemberStatus membershipStatus,
                                     CompanyStatus companyStatus) {
        UserAccount user = UserAccount.activeSocial(
                "member@example.com", "member", AuthProvider.GITHUB, "subject-member");
        Company company = Company.pendingVerification(
                "테스트 주식회사", "테스트", "a".repeat(64), "***-**-12345", null, user);
        company.changeStatus(companyStatus);
        if (role == CompanyMemberRole.OWNER) {
            CompanyMember owner = CompanyMember.activeOwner(
                    company, user, LocalDateTime.of(2026, 9, 8, 10, 0));
            if (membershipStatus != CompanyMemberStatus.ACTIVE) {
                owner.changeStatus(membershipStatus, LocalDateTime.of(2026, 9, 8, 11, 0));
            }
            return owner;
        }
        CompanyMember member = CompanyMember.invited(company, user, role, user);
        if (membershipStatus != CompanyMemberStatus.INVITED) {
            member.changeStatus(membershipStatus, LocalDateTime.of(2026, 9, 8, 11, 0));
        }
        return member;
    }
}
