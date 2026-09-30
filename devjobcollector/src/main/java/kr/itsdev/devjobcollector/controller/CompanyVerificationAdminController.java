package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationRejectRequest;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/company-verification-requests")
public class CompanyVerificationAdminController {
    private final CompanyVerificationService verificationService;
    private final CompanyEvidenceService evidenceService;

    public CompanyVerificationAdminController(CompanyVerificationService verificationService,
                                              CompanyEvidenceService evidenceService) {
        this.verificationService = verificationService;
        this.evidenceService = evidenceService;
    }

    @GetMapping("/{requestId}/evidence")
    public ResponseEntity<byte[]> downloadEvidence(@AuthenticationPrincipal String subject,
                                                   @PathVariable Long requestId) {
        CompanyEvidenceService.Evidence evidence = evidenceService.readForAdmin(subject, requestId);
        String extension = evidence.contentType().equals("application/pdf") ? "pdf"
                : evidence.contentType().equals("image/png") ? "png" : "jpg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=business-registration." + extension)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(evidence.contentType()))
                .body(evidence.content());
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
