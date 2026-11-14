package kr.itsdev.devjobcollector.admin.companies;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/admin/companies/public-seeds")
public class AdminPublicSeedController {
    private final AdminPublicSeedImportService service;
    private final AdminPublicSeedQueryService query;
    private final AdminPublicSeedReviewService review;
    private final AdminPublicSeedDiscoveryService discovery;
    private final PublicSeedBoardVerificationService verification;
    private final PublicSeedTargetApprovalService approval;

    public AdminPublicSeedController(AdminPublicSeedImportService service, AdminPublicSeedQueryService query,
                                     AdminPublicSeedReviewService review, AdminPublicSeedDiscoveryService discovery,
                                     PublicSeedBoardVerificationService verification, PublicSeedTargetApprovalService approval) {
        this.service = service;
        this.query = query;
        this.review = review;
        this.discovery = discovery;
        this.verification = verification;
        this.approval = approval;
    }

    @PutMapping("/{id}/homepage")
    public ResponseEntity<Void> setHomepage(@PathVariable long id, @RequestBody HomepageRequest body,
                                            @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        review.setHomepage(id, body.expectedVersion(), body.url(), principal,
                (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    public record HomepageRequest(String url, Long expectedVersion) { }

    @PostMapping("/{id}/discover")
    public Map<String, Object> discoverHomepage(@PathVariable long id, @RequestBody DiscoveryRequest body,
                                            @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
        return Map.of("data", Map.of("candidateCount", discovery.discover(id, body.expectedVersion(),
                principal, requestId, request.getRemoteAddr(), request.getHeader("User-Agent"))), "requestId", requestId);
    }

    @GetMapping("/{id}/ats-candidates")
    public ResponseEntity<Map<String, Object>> candidates(@PathVariable long id,
                                                           @RequestParam(defaultValue = "0") long afterId,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("data", discovery.list(id, afterId, size),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE)));
    }

    public record DiscoveryRequest(Long expectedVersion) { }

    @PostMapping("/{id}/ats-candidates/{candidateId}/reject")
    public ResponseEntity<Void> rejectCandidate(@PathVariable long id, @PathVariable long candidateId,
                                                 @RequestBody CandidateRejection body,
                                                 @AuthenticationPrincipal AdminPrincipal principal,
                                                 HttpServletRequest request) {
        discovery.reject(id, candidateId, body.reason(), principal,
                (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    public record CandidateRejection(String reason) { }

    @PostMapping("/{id}/ats-candidates/{candidateId}/review")
    public ResponseEntity<Void> startReview(@PathVariable long id, @PathVariable long candidateId,
                                            @RequestBody CandidateReview body,
                                            @AuthenticationPrincipal AdminPrincipal principal,
                                            HttpServletRequest request) {
        discovery.startReview(id, candidateId, body.expectedSeedVersion(), body.note(), body.referenceUrl(), principal,
                (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    public record CandidateReview(Long expectedSeedVersion, String note, String referenceUrl) { }

    @PostMapping("/{id}/ats-candidates/{candidateId}/verify-board")
    public Map<String, Object> verifyBoard(@PathVariable long id, @PathVariable long candidateId,
                                           @RequestBody BoardVerification body,
                                           @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
        return Map.of("data", Map.of("jobCount", verification.verify(id, candidateId, body.expectedSeedVersion(),
                principal, requestId, request.getRemoteAddr(), request.getHeader("User-Agent"))), "requestId", requestId);
    }

    public record BoardVerification(Long expectedSeedVersion) { }

    @PostMapping("/{id}/ats-candidates/{candidateId}/approve-target")
    public Map<String, Object> approveTarget(@PathVariable long id, @PathVariable long candidateId,
                                             @RequestBody TargetApproval body,
                                             @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
        return Map.of("data", Map.of("targetId", approval.approve(id, candidateId, body.expectedSeedVersion(),
                body.companyName(), body.ownershipConfirmed(), principal, requestId,
                request.getRemoteAddr(), request.getHeader("User-Agent"))), "requestId", requestId);
    }

    public record TargetApproval(Long expectedSeedVersion, String companyName, boolean ownershipConfirmed) { }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(defaultValue = "0") long afterId,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("data", query.list(afterId, size),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE)));
    }

    @GetMapping("/paged")
    public ResponseEntity<Map<String, Object>> page(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("data", query.page(page, size),
                "requestId", request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importFile(@RequestParam("file") MultipartFile file,
                                          @RequestParam(defaultValue = "CP949") String encoding,
                                          @AuthenticationPrincipal AdminPrincipal principal,
                                          HttpServletRequest request) {
        String requestId = (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
        return Map.of("data", service.importFile(file, encoding, principal, requestId, request.getRemoteAddr(),
                request.getHeader("User-Agent")), "requestId", requestId);
    }
}
