package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.hardening.SecurityAction;
import kr.itsdev.devjobcollector.security.hardening.SecurityAuditEventType;
import kr.itsdev.devjobcollector.security.hardening.SecurityHardeningService;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class CompanySignupFacadeTest {
    private CompanyRepository companyRepository;
    private CompanyMemberRepository memberRepository;
    private CurrentMemberService currentMemberService;
    private SecurityHardeningService hardeningService;
    private CompanySignupFacade facade;
    private UserAccount owner;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        memberRepository = mock(CompanyMemberRepository.class);
        currentMemberService = mock(CurrentMemberService.class);
        hardeningService = mock(SecurityHardeningService.class);
        facade = new CompanySignupFacade(
                companyRepository, memberRepository, currentMemberService, hardeningService);
        owner = UserAccount.activeSocial(
                "owner@example.com", "owner", AuthProvider.GITHUB, "company-owner-subject");
        when(currentMemberService.requireCurrentMember("42")).thenReturn(owner);
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.saveAndFlush(any(CompanyMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsPendingCompanyAndActiveOwnerWithoutPersistingPlaintextNumber() {
        CompanySignupRequest request = request("123-45-67890");
        var response = facade.signup("42", request);

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        ArgumentCaptor<CompanyMember> memberCaptor = ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyRepository).saveAndFlush(companyCaptor.capture());
        verify(memberRepository).saveAndFlush(memberCaptor.capture());

        Company company = companyCaptor.getValue();
        CompanyMember membership = memberCaptor.getValue();
        assertThat(company.getBusinessNumberHash()).hasSize(64).doesNotContain("1234567890");
        assertThat(company.getBusinessNumberMasked()).isEqualTo("***-**-67890");
        assertThat(request.toString()).contains("businessNumber=<redacted>")
                .doesNotContain("123-45-67890", "1234567890");
        assertThat(membership.getCompany()).isSameAs(company);
        assertThat(membership.getUser()).isSameAs(owner);
        assertThat(response.companyStatus()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
        assertThat(response.role()).isEqualTo(CompanyMemberRole.OWNER);
        assertThat(response.membershipStatus()).isEqualTo(CompanyMemberStatus.ACTIVE);
        verify(hardeningService).checkRateLimit(eq(SecurityAction.COMPANY_SIGNUP),
                eq("actor:null"), eq("business:" + company.getBusinessNumberHash()));
        verify(hardeningService).audit(SecurityAuditEventType.COMPANY_CREATED,
                null, null, null, null, CompanyStatus.PENDING_VERIFICATION.name());
    }

    @Test
    void rejectsKnownBusinessNumberBeforeWriting() {
        String hash = BusinessNumberProtection.protect("123-45-67890").hash();
        when(companyRepository.existsByBusinessNumberHash(hash)).thenReturn(true);

        assertThatThrownBy(() -> facade.signup("42", request("123-45-67890")))
                .isInstanceOf(CompanyAlreadyExistsException.class);

        verify(companyRepository, never()).saveAndFlush(any(Company.class));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void normalizesDatabaseUniqueRaceToCompanyConflict() {
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> facade.signup("42", request("123-45-67890")))
                .isInstanceOf(CompanyAlreadyExistsException.class)
                .hasMessage(CompanyAlreadyExistsException.ERROR_CODE);

        verifyNoInteractions(memberRepository);
    }

    private CompanySignupRequest request(String businessNumber) {
        return new CompanySignupRequest(
                "테스트 주식회사", "테스트", businessNumber, "https://example.com");
    }
}
