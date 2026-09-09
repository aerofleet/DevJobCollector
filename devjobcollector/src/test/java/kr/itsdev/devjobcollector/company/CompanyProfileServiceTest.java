package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompanyProfileServiceTest {
    private CompanyMemberRepository memberRepository;
    private CompanyVerificationRequestRepository verificationRepository;
    private CurrentMemberService currentMemberService;
    private CompanyProfileService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(CompanyMemberRepository.class);
        verificationRepository = mock(CompanyVerificationRequestRepository.class);
        currentMemberService = mock(CurrentMemberService.class);
        service = new CompanyProfileService(
                memberRepository, verificationRepository, currentMemberService);
    }

    @Test
    void returnsNonLeftMembershipsWithLatestVerificationWithoutSensitiveFields() {
        UserAccount member = mock(UserAccount.class);
        when(member.getId()).thenReturn(42L);
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(7L);
        when(company.getLegalName()).thenReturn("테스트 주식회사");
        when(company.getDisplayName()).thenReturn("테스트");
        when(company.getBusinessNumberMasked()).thenReturn("123-45-*****");
        when(company.getStatus()).thenReturn(CompanyStatus.PENDING_VERIFICATION);
        CompanyMember membership = mock(CompanyMember.class);
        when(membership.getId()).thenReturn(11L);
        when(membership.getCompany()).thenReturn(company);
        when(membership.getRole()).thenReturn(CompanyMemberRole.OWNER);
        when(membership.getStatus()).thenReturn(CompanyMemberStatus.ACTIVE);
        CompanyVerificationRequest verification = mock(CompanyVerificationRequest.class);
        when(verification.getId()).thenReturn(9L);
        when(verification.getStatus()).thenReturn(CompanyVerificationStatus.PENDING);
        when(verification.getRequestedAt()).thenReturn(LocalDateTime.of(2026, 9, 9, 11, 0));

        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(memberRepository.findAllByUser_IdAndStatusNotOrderByCompany_IdAsc(
                42L, CompanyMemberStatus.LEFT)).thenReturn(List.of(membership));
        when(verificationRepository.findTopByCompany_IdOrderByRequestedAtDescIdDesc(7L))
                .thenReturn(Optional.of(verification));

        var result = service.getMyCompanies("42");

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.companyId()).isEqualTo(7L);
            assertThat(summary.businessNumberMasked()).isEqualTo("123-45-*****");
            assertThat(summary.verificationStatus()).isEqualTo(CompanyVerificationStatus.PENDING);
        });
        verify(memberRepository).findAllByUser_IdAndStatusNotOrderByCompany_IdAsc(
                42L, CompanyMemberStatus.LEFT);
    }

    @Test
    void returnsEmptyListWhenMemberHasNoCompanyRelationship() {
        UserAccount member = mock(UserAccount.class);
        when(member.getId()).thenReturn(42L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(memberRepository.findAllByUser_IdAndStatusNotOrderByCompany_IdAsc(
                42L, CompanyMemberStatus.LEFT)).thenReturn(List.of());

        assertThat(service.getMyCompanies("42")).isEmpty();
    }
}
