package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberInvitationRequest;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class CompanyMemberManagementServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);
    private CompanyRepository companyRepository;
    private CompanyMemberRepository memberRepository;
    private UserAccountRepository userRepository;
    private CurrentMemberService currentMemberService;
    private CompanyAuthorizationService authorizationService;
    private CompanyMemberManagementService service;
    private UserAccount actor;
    private Company company;
    private CompanyMember actorMembership;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        memberRepository = mock(CompanyMemberRepository.class);
        userRepository = mock(UserAccountRepository.class);
        currentMemberService = mock(CurrentMemberService.class);
        authorizationService = mock(CompanyAuthorizationService.class);
        service = new CompanyMemberManagementService(
                companyRepository, memberRepository, userRepository, currentMemberService,
                authorizationService,
                Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC));
        actor = user(10L, "owner@example.com", UserAccountStatus.ACTIVE);
        company = mock(Company.class);
        when(company.getId()).thenReturn(1L);
        when(company.getStatus()).thenReturn(CompanyStatus.VERIFIED);
        actorMembership = mock(CompanyMember.class);
        when(actorMembership.getCompany()).thenReturn(company);
        when(actorMembership.getRole()).thenReturn(CompanyMemberRole.OWNER);
        when(actorMembership.getStatus()).thenReturn(CompanyMemberStatus.ACTIVE);
        when(currentMemberService.requireCurrentMember("10")).thenReturn(actor);
        when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
        when(memberRepository.findByCompanyAndUserForUpdate(1L, 10L))
                .thenReturn(Optional.of(actorMembership));
    }

    @Test
    void listsNonLeftMembersAfterAuthorization() {
        CompanyMember member = member(20L, CompanyMemberRole.VIEWER, CompanyMemberStatus.ACTIVE);
        when(memberRepository.findAllByCompany_IdAndStatusNotOrderByIdAsc(
                1L, CompanyMemberStatus.LEFT)).thenReturn(List.of(member));

        var responses = service.listMembers("10", 1L);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.userId()).isEqualTo(20L);
            assertThat(response.role()).isEqualTo(CompanyMemberRole.VIEWER);
        });
        verify(authorizationService).authorize(1L, 10L, CompanyPermission.VIEW_MEMBERS);
    }

    @Test
    void ownerInvitesAdminAsInvitedMembership() {
        UserAccount invitee = user(20L, "invitee@example.com", UserAccountStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("invitee@example.com"))
                .thenReturn(Optional.of(invitee));
        when(memberRepository.findByCompanyAndUserForUpdate(1L, 20L)).thenReturn(Optional.empty());
        when(memberRepository.saveAndFlush(any(CompanyMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CompanyMemberInvitationRequest(
                " invitee@example.com ", CompanyMemberRole.ADMIN);
        var response = service.invite("10", 1L, request);

        ArgumentCaptor<CompanyMember> captor = ArgumentCaptor.forClass(CompanyMember.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(CompanyMemberRole.ADMIN);
        assertThat(captor.getValue().getStatus()).isEqualTo(CompanyMemberStatus.INVITED);
        assertThat(captor.getValue().getInvitedBy()).isSameAs(actor);
        assertThat(response.email()).isEqualTo("invitee@example.com");
        assertThat(request.toString()).contains("email=<redacted>")
                .doesNotContain("invitee@example.com");
        verify(authorizationService).authorize(actorMembership, CompanyPermission.ASSIGN_ADMIN);
    }

    @Test
    void reinvitesLeftMemberWithoutCreatingDuplicateRow() {
        UserAccount invitee = user(20L, "returning@example.com", UserAccountStatus.ACTIVE);
        CompanyMember existing = CompanyMember.invited(
                company, invitee, CompanyMemberRole.VIEWER, actor);
        existing.changeStatus(CompanyMemberStatus.LEFT, NOW.minusDays(1));
        when(userRepository.findByEmailIgnoreCase("returning@example.com"))
                .thenReturn(Optional.of(invitee));
        when(memberRepository.findByCompanyAndUserForUpdate(1L, 20L))
                .thenReturn(Optional.of(existing));

        var response = service.invite("10", 1L,
                new CompanyMemberInvitationRequest("returning@example.com", CompanyMemberRole.RECRUITER));

        assertThat(response.status()).isEqualTo(CompanyMemberStatus.INVITED);
        assertThat(response.role()).isEqualTo(CompanyMemberRole.RECRUITER);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExistingActiveOrInvitedMembership() {
        UserAccount invitee = user(20L, "existing@example.com", UserAccountStatus.ACTIVE);
        CompanyMember existing = member(
                20L, CompanyMemberRole.VIEWER, CompanyMemberStatus.INVITED);
        when(userRepository.findByEmailIgnoreCase("existing@example.com"))
                .thenReturn(Optional.of(invitee));
        when(memberRepository.findByCompanyAndUserForUpdate(1L, 20L))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.invite("10", 1L,
                new CompanyMemberInvitationRequest("existing@example.com", CompanyMemberRole.VIEWER)))
                .isInstanceOf(CompanyMemberManagementException.class)
                .hasMessage("COMPANY_MEMBER_ALREADY_EXISTS");
    }

    @Test
    void normalizesInvitationUniqueRaceToConflict() {
        UserAccount invitee = user(20L, "race@example.com", UserAccountStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("race@example.com")).thenReturn(Optional.of(invitee));
        when(memberRepository.findByCompanyAndUserForUpdate(1L, 20L)).thenReturn(Optional.empty());
        when(memberRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.invite("10", 1L,
                new CompanyMemberInvitationRequest("race@example.com", CompanyMemberRole.VIEWER)))
                .isInstanceOf(CompanyMemberManagementException.class)
                .hasMessage("COMPANY_MEMBER_ALREADY_EXISTS");
    }

    @Test
    void changingExistingAdminRequiresOwnerOnlyPermission() {
        CompanyMember target = member(20L, CompanyMemberRole.ADMIN, CompanyMemberStatus.ACTIVE);
        when(memberRepository.findByIdAndCompanyForUpdate(20L, 1L)).thenReturn(Optional.of(target));

        var response = service.changeRole("10", 1L, 20L, CompanyMemberRole.RECRUITER);

        assertThat(response.role()).isEqualTo(CompanyMemberRole.RECRUITER);
        verify(authorizationService).authorize(actorMembership, CompanyPermission.ASSIGN_ADMIN);
    }

    @Test
    void blocksDemotionOfLastActiveOwner() {
        CompanyMember target = member(20L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE);
        when(memberRepository.findByIdAndCompanyForUpdate(20L, 1L)).thenReturn(Optional.of(target));
        when(memberRepository.countByCompany_IdAndRoleAndStatus(
                1L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(
                "10", 1L, 20L, CompanyMemberRole.ADMIN))
                .isInstanceOf(LastActiveOwnerException.class);

        assertThat(target.getRole()).isEqualTo(CompanyMemberRole.OWNER);
    }

    @Test
    void removingMemberTransitionsToLeftWithoutDeletingRow() {
        CompanyMember target = member(20L, CompanyMemberRole.RECRUITER, CompanyMemberStatus.ACTIVE);
        when(memberRepository.findByIdAndCompanyForUpdate(20L, 1L)).thenReturn(Optional.of(target));

        service.remove("10", 1L, 20L);

        assertThat(target.getStatus()).isEqualTo(CompanyMemberStatus.LEFT);
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void removingOwnerRequiresOwnerOnlyPermissionAndAnotherActiveOwner() {
        CompanyMember target = member(20L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE);
        when(memberRepository.findByIdAndCompanyForUpdate(20L, 1L)).thenReturn(Optional.of(target));
        when(memberRepository.countByCompany_IdAndRoleAndStatus(
                1L, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).thenReturn(2L);

        service.remove("10", 1L, 20L);

        assertThat(target.getStatus()).isEqualTo(CompanyMemberStatus.LEFT);
        verify(authorizationService).authorize(actorMembership, CompanyPermission.TRANSFER_OWNERSHIP);
    }

    @Test
    void rejectsInactiveInviteeBeforeWriting() {
        UserAccount invitee = user(20L, "inactive@example.com", UserAccountStatus.SUSPENDED);
        when(userRepository.findByEmailIgnoreCase("inactive@example.com"))
                .thenReturn(Optional.of(invitee));

        assertThatThrownBy(() -> service.invite("10", 1L,
                new CompanyMemberInvitationRequest("inactive@example.com", CompanyMemberRole.VIEWER)))
                .isInstanceOf(CompanyMemberManagementException.class)
                .hasMessage("COMPANY_INVITEE_NOT_FOUND");

        verify(memberRepository, never()).saveAndFlush(any());
    }

    private CompanyMember member(Long userId, CompanyMemberRole role, CompanyMemberStatus status) {
        UserAccount user = user(userId, "member-" + userId + "@example.com", UserAccountStatus.ACTIVE);
        CompanyMember member;
        if (role == CompanyMemberRole.OWNER) {
            member = CompanyMember.activeOwner(company, user, NOW.minusDays(1));
        } else {
            member = CompanyMember.invited(company, user, role, actor);
            if (status != CompanyMemberStatus.INVITED) {
                member.changeStatus(status, NOW.minusDays(1));
            }
        }
        return member;
    }

    private UserAccount user(Long id, String email, UserAccountStatus status) {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getName()).thenReturn(email.substring(0, email.indexOf('@')));
        when(user.getStatus()).thenReturn(status);
        return user;
    }
}
