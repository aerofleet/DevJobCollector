package kr.itsdev.devjobcollector.admin.companies;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.company.Company;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import kr.itsdev.devjobcollector.company.CompanyRepository;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequest;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequestRepository;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminCompanyReviewServiceTest {
    @Mock CompanyRepository companies;
    @Mock CompanyVerificationRequestRepository requests;
    @Mock AdminCompanyService views;
    @Mock AdminAuditLogRepository audit;
    @Mock CompanyEvidenceService evidenceService;
    @Mock Company company;
    @Mock CompanyVerificationRequest request;
    private AdminCompanyReviewService service;

    @BeforeEach
    void setUp() {
        service = new AdminCompanyReviewService(companies, requests, views, audit, evidenceService);
    }

    @Test
    void approvesOnlyPendingRequestWithStoredEvidence() {
        pendingFixture();
        when(evidenceService.readForAdminCompany(7L, 9L))
                .thenReturn(new CompanyEvidenceService.Evidence("image/png", new byte[] {1}));

        service.review(7L, 9L, true, null, admin(), "request-id", "127.0.0.1", "test");

        verify(request).approveByAdmin(eq(1L), any());
        verify(company).changeStatus(CompanyStatus.VERIFIED);
        verify(audit).save(any());
    }

    @Test
    void rejectsRequestWithoutStoredEvidence() {
        pendingFixture();
        when(evidenceService.readForAdminCompany(7L, 9L))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.review(7L, 9L, true, null,
                admin(), "request-id", "127.0.0.1", "test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VERIFICATION_EVIDENCE_MISSING");
        verify(request, never()).approveByAdmin(any(), any());
        verify(audit, never()).save(any());
    }

    @Test
    void rejectionRequiresReason() {
        assertThatThrownBy(() -> service.review(7L, 9L, false, " ",
                admin(), "request-id", "127.0.0.1", "test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_REJECTION_REASON");
    }

    @Test
    void requestMustBelongToSelectedCompany() {
        when(companies.findByIdForUpdate(7L)).thenReturn(Optional.of(company));
        when(requests.findByIdForUpdate(9L)).thenReturn(Optional.of(request));
        when(request.getCompany()).thenReturn(company);
        when(company.getId()).thenReturn(8L);
        assertThatThrownBy(() -> service.review(7L, 9L, true, null,
                admin(), "request-id", "127.0.0.1", "test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VERIFICATION_REQUEST_NOT_FOUND");
        verify(audit, never()).save(any());
    }

    private void pendingFixture() {
        when(companies.findByIdForUpdate(7L)).thenReturn(Optional.of(company));
        when(requests.findByIdForUpdate(9L)).thenReturn(Optional.of(request));
        when(request.getCompany()).thenReturn(company);
        when(company.getId()).thenReturn(7L);
        when(company.getStatus()).thenReturn(CompanyStatus.PENDING_VERIFICATION);
        when(request.getStatus()).thenReturn(CompanyVerificationStatus.PENDING);
    }

    private AdminPrincipal admin() {
        return new AdminPrincipal(1L, "admin@example.com", "Admin", AdminRole.REVIEWER,
                Set.of(), "session", "a".repeat(64));
    }
}
