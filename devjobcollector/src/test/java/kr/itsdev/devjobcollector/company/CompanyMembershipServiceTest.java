package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CompanyMembershipServiceTest {
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final CompanyMemberRepository memberRepository = mock(CompanyMemberRepository.class);
    private final CompanyMembershipService service =
            new CompanyMembershipService(companyRepository, memberRepository);
    private Company company;
    private UserAccount owner;
    private CompanyMember membership;

    @BeforeEach
    void setUp() {
        owner = user("owner@example.com");
        company = Company.pendingVerification("테스트 주식회사", "테스트", "a".repeat(64),
                "***-**-12345", null, owner);
        membership = CompanyMember.activeOwner(
                company, owner, LocalDateTime.of(2026, 9, 7, 10, 0));
        when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
        when(memberRepository.findByCompanyAndUserForUpdate(1L, 10L))
                .thenReturn(Optional.of(membership));
    }

    @Test
    void blocksRoleChangeThatWouldRemoveLastActiveOwner() {
        when(memberRepository.countByCompany_IdAndRoleAndStatus(
                1L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(1L, 10L, CompanyMemberRole.ADMIN))
                .isInstanceOf(LastActiveOwnerException.class)
                .hasMessage("LAST_ACTIVE_COMPANY_OWNER");

        assertThat(membership.getRole()).isEqualTo(CompanyMemberRole.OWNER);
        InOrder order = inOrder(companyRepository, memberRepository);
        order.verify(companyRepository).findByIdForUpdate(1L);
        order.verify(memberRepository).findByCompanyAndUserForUpdate(1L, 10L);
    }

    @Test
    void allowsRoleChangeWhenAnotherActiveOwnerExists() {
        when(memberRepository.countByCompany_IdAndRoleAndStatus(
                1L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).thenReturn(2L);

        service.changeRole(1L, 10L, CompanyMemberRole.ADMIN);

        assertThat(membership.getRole()).isEqualTo(CompanyMemberRole.ADMIN);
    }

    @Test
    void blocksLeavingWhenMemberIsLastActiveOwner() {
        when(memberRepository.countByCompany_IdAndRoleAndStatus(
                1L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeStatus(
                1L, 10L, CompanyMemberStatus.LEFT, LocalDateTime.of(2026, 9, 7, 11, 0)))
                .isInstanceOf(LastActiveOwnerException.class);

        assertThat(membership.getStatus()).isEqualTo(CompanyMemberStatus.ACTIVE);
    }

    @Test
    void doesNotCountInactiveOwnersAsProtectionForLastActiveOwner() {
        when(memberRepository.countByCompany_IdAndRoleAndStatus(
                1L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(1L, 10L, CompanyMemberRole.VIEWER))
                .isInstanceOf(LastActiveOwnerException.class);

        verify(memberRepository).countByCompany_IdAndRoleAndStatus(
                1L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE);
    }

    private UserAccount user(String email) {
        return UserAccount.activeSocial(email, email, AuthProvider.GITHUB, "subject-" + email);
    }
}
