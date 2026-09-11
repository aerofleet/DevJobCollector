package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.hardening.SecurityAction;
import kr.itsdev.devjobcollector.security.hardening.SecurityAuditEventType;
import kr.itsdev.devjobcollector.security.hardening.SecurityHardeningService;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompanyVerificationServiceTest {
    private CompanyRepository companyRepository;
    private CompanyMemberRepository memberRepository;
    private CompanyVerificationRequestRepository requestRepository;
    private CurrentMemberService currentMemberService;
    private SecurityHardeningService hardeningService;
    private CompanyVerificationService service;
    private UserAccount owner;
    private Company company;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        memberRepository = mock(CompanyMemberRepository.class);
        requestRepository = mock(CompanyVerificationRequestRepository.class);
        currentMemberService = mock(CurrentMemberService.class);
        hardeningService = mock(SecurityHardeningService.class);
        service = new CompanyVerificationService(
                companyRepository, memberRepository, requestRepository, currentMemberService,
                hardeningService,
                Clock.fixed(Instant.parse("2026-09-08T01:00:00Z"), ZoneOffset.UTC));
        owner = mock(UserAccount.class);
        when(owner.getId()).thenReturn(10L);
        company = Company.pendingVerification(
                "테스트 주식회사", "테스트", "a".repeat(64), "***-**-12345", null, owner);
    }

    @Test
    void activeOwnerSubmitsPendingRequestWithoutExposingEvidence() {
        CompanyMember membership = mock(CompanyMember.class);
        when(currentMemberService.requireCurrentMember("10")).thenReturn(owner);
        when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
        when(memberRepository.findByCompany_IdAndUser_Id(1L, 10L)).thenReturn(Optional.of(membership));
        when(membership.isActiveOwner()).thenReturn(true);
        when(membership.getUser()).thenReturn(owner);
        when(requestRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit("10", 1L, submitRequest());

        assertThat(response.requestStatus()).isEqualTo(CompanyVerificationStatus.PENDING);
        assertThat(response.requestedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 1, 0));
        assertThat(submitRequest().toString())
                .contains("evidenceObjectKey=<redacted>")
                .doesNotContain("verification/1/evidence.pdf");
        verify(hardeningService).checkRateLimit(
                SecurityAction.COMPANY_VERIFICATION_REQUEST, "actor:10", "company:1");
        verify(hardeningService).audit(SecurityAuditEventType.COMPANY_VERIFICATION_REQUESTED,
                10L, 10L, 1L, null, CompanyVerificationStatus.PENDING.name());
    }

    @Test
    void rejectsNonOwnerBeforeWritingRequest() {
        CompanyMember membership = mock(CompanyMember.class);
        when(currentMemberService.requireCurrentMember("10")).thenReturn(owner);
        when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
        when(memberRepository.findByCompany_IdAndUser_Id(1L, 10L)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.submit("10", 1L, submitRequest()))
                .isInstanceOf(CompanyVerificationException.class)
                .hasMessage("COMPANY_OWNER_REQUIRED");
        verify(requestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicatePendingRequestUnderCompanyLock() {
        CompanyMember membership = mock(CompanyMember.class);
        when(currentMemberService.requireCurrentMember("10")).thenReturn(owner);
        when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
        when(memberRepository.findByCompany_IdAndUser_Id(1L, 10L)).thenReturn(Optional.of(membership));
        when(membership.isActiveOwner()).thenReturn(true);
        when(requestRepository.existsByCompany_IdAndStatus(1L, CompanyVerificationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit("10", 1L, submitRequest()))
                .isInstanceOf(CompanyVerificationException.class)
                .hasMessage("COMPANY_VERIFICATION_PENDING_EXISTS");
    }

    @Test
    void platformAdminApprovesPendingRequestAndVerifiesCompany() {
        UserAccount admin = mock(UserAccount.class);
        when(admin.getId()).thenReturn(99L);
        when(admin.getRole()).thenReturn("PLATFORM_ADMIN");
        CompanyVerificationRequest request = CompanyVerificationRequest.pending(
                company, owner, CompanyVerificationMethod.BUSINESS_REGISTRATION_DOCUMENT,
                "verification/1/evidence.pdf", LocalDateTime.of(2026, 9, 8, 0, 0));
        when(currentMemberService.requireCurrentMember("20")).thenReturn(admin);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(companyRepository.findByIdForUpdate(null)).thenReturn(Optional.of(company));
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));

        var response = service.approve("20", 5L);

        assertThat(response.requestStatus()).isEqualTo(CompanyVerificationStatus.APPROVED);
        assertThat(response.companyStatus()).isEqualTo(CompanyStatus.VERIFIED);
        assertThat(response.reviewedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 1, 0));
        verify(requestRepository).flush();
        verify(hardeningService).checkRateLimit(
                SecurityAction.COMPANY_VERIFICATION_REVIEW, "actor:99", "company:null");
        verify(hardeningService).audit(SecurityAuditEventType.COMPANY_VERIFICATION_APPROVED,
                99L, 10L, null, CompanyVerificationStatus.PENDING.name(),
                CompanyVerificationStatus.APPROVED.name());
    }

    @Test
    void rejectsReviewByRegularUserBeforeLoadingRequest() {
        UserAccount regularUser = mock(UserAccount.class);
        when(regularUser.getRole()).thenReturn("USER");
        when(currentMemberService.requireCurrentMember("30")).thenReturn(regularUser);

        assertThatThrownBy(() -> service.approve("30", 5L))
                .isInstanceOf(CompanyVerificationException.class)
                .hasMessage("PLATFORM_ADMIN_REQUIRED");
        verify(requestRepository, never()).findById(5L);
    }

    private CompanyVerificationSubmitRequest submitRequest() {
        return new CompanyVerificationSubmitRequest(
                CompanyVerificationMethod.BUSINESS_REGISTRATION_DOCUMENT,
                "verification/1/evidence.pdf");
    }
}
