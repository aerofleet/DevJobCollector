package kr.itsdev.devjobcollector.admin.companies;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class AdminCompanyController {
    private final AdminCompanyService service;
    private final CompanyEvidenceService evidenceService;

    public AdminCompanyController(AdminCompanyService service, CompanyEvidenceService evidenceService) {
        this.service = service;
        this.evidenceService = evidenceService;
    }

    @GetMapping("/{id}/verification-requests/{requestId}/evidence")
    public ResponseEntity<byte[]> evidence(@PathVariable Long id, @PathVariable Long requestId) {
        CompanyEvidenceService.Evidence evidence = evidenceService.readForAdminCompany(id, requestId);
        String extension = evidence.contentType().equals("application/pdf") ? "pdf"
                : evidence.contentType().equals("image/png") ? "png" : "jpg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=business-registration." + extension)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(evidence.contentType()))
                .body(evidence.content());
    }

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
