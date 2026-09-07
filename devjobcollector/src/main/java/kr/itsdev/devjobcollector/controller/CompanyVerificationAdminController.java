package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationRejectRequest;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/company-verification-requests")
public class CompanyVerificationAdminController {
    private final CompanyVerificationService verificationService;

    public CompanyVerificationAdminController(CompanyVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/{requestId}/approve")
    public CompanyVerificationResponse approve(@AuthenticationPrincipal String subject,
                                               @PathVariable Long requestId) {
        return verificationService.approve(subject, requestId);
    }

    @PostMapping("/{requestId}/reject")
    public CompanyVerificationResponse reject(@AuthenticationPrincipal String subject,
                                              @PathVariable Long requestId,
                                              @Valid @RequestBody CompanyVerificationRejectRequest request) {
        return verificationService.reject(subject, requestId, request.reason());
    }
}
