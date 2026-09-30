package kr.itsdev.devjobcollector.admin.companies;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class AdminCompanyController {
    private final AdminCompanyService service;
    private final CompanyEvidenceService evidenceService;
    private final AdminCompanyReviewService reviewService;
    private final AdminAuditLogRepository audit;

    public AdminCompanyController(AdminCompanyService service, CompanyEvidenceService evidenceService,
                                  AdminCompanyReviewService reviewService,
                                  AdminAuditLogRepository audit) {
        this.service = service;
        this.evidenceService = evidenceService;
        this.reviewService = reviewService;
        this.audit = audit;
    }

    @GetMapping("/{id}/verification-requests/{requestId}/evidence")
    public ResponseEntity<byte[]> evidence(@PathVariable Long id, @PathVariable Long requestId,
                                           @AuthenticationPrincipal AdminPrincipal principal,
                                           HttpServletRequest request) {
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        }
        CompanyEvidenceService.Evidence evidence = evidenceService.readForAdminCompany(id, requestId);
        audit.save(AdminAuditLog.record(principal.id(), "COMPANY_EVIDENCE_READ",
                "COMPANY_VERIFICATION_REQUEST", requestId.toString(), null, null, null,
                AdminAuditResult.SUCCESS, requestId(request),
                truncate(request.getRemoteAddr(), 45), truncate(request.getHeader("User-Agent"), 500)));
        String extension = evidence.contentType().equals("application/pdf") ? "pdf"
                : evidence.contentType().equals("image/png") ? "png" : "jpg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=business-registration." + extension)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header("Cross-Origin-Resource-Policy", "same-site")
                .header("X-Frame-Options", "DENY")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(evidence.contentType()))
                .body(evidence.content());
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @PostMapping("/{id}/verification-requests/{requestId}/approve")
    public Map<String, Object> approve(@PathVariable Long id, @PathVariable Long requestId,
                                       @AuthenticationPrincipal AdminPrincipal principal,
                                       HttpServletRequest request) {
        return Map.of("data", reviewService.review(id, requestId, true, null, principal,
                requestId(request), request.getRemoteAddr(), request.getHeader("User-Agent")),
                "requestId", requestId(request));
    }

    @PostMapping("/{id}/verification-requests/{requestId}/reject")
    public Map<String, Object> reject(@PathVariable Long id, @PathVariable Long requestId,
                                      @Valid @RequestBody RejectionRequest body,
                                      @AuthenticationPrincipal AdminPrincipal principal,
                                      HttpServletRequest request) {
        return Map.of("data", reviewService.review(id, requestId, false, body.reason(), principal,
                requestId(request), request.getRemoteAddr(), request.getHeader("User-Agent")),
                "requestId", requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
    }

    public record RejectionRequest(@NotBlank @Size(max = 500) String reason) {}

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) CompanyStatus status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    HttpServletRequest request) {
        return Map.of("data", service.list(keyword, status, page, size),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, HttpServletRequest request) {
        return Map.of("data", service.detail(id),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
    }
}
