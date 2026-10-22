package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kr.itsdev.devjobcollector.ai.ResumeContentReviewService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/resume-analyses/{id}/content-review")
@ConditionalOnProperty(name="ai.analysis.enabled", havingValue="true")
public class ResumeContentReviewController {
    private final ResumeContentReviewService service;
    public ResumeContentReviewController(ResumeContentReviewService service) { this.service=service; }
    @PostMapping("/preview") public ResponseEntity<?> preview(@AuthenticationPrincipal String subject, @PathVariable long id) { return noStore(service.preview(subject,id)); }
    @GetMapping public ResponseEntity<?> get(@AuthenticationPrincipal String subject, @PathVariable long id) { return noStore(service.get(subject,id)); }
    @PostMapping public ResponseEntity<?> review(@AuthenticationPrincipal String subject, @PathVariable long id, @Valid @RequestBody Request request) {
        return noStore(service.review(subject,id,request.consentVersion(),request.previewHash()));
    }
    public record Request(@NotBlank @Size(max=50) String consentVersion, @NotBlank @Pattern(regexp="[a-f0-9]{64}") String previewHash) {}
    @PutMapping("/confirm") public ResponseEntity<?> confirm(@AuthenticationPrincipal String subject, @PathVariable long id, @Valid @RequestBody Confirmation request) {
        return noStore(service.confirm(subject,id,request.findingId(),request.option()));
    }
    public record Confirmation(@NotBlank @Size(max=64) String findingId, @NotNull @Min(-1) @Max(2) Integer option) {}
    private ResponseEntity<?> noStore(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> error(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code", error.getReason()==null ? "CONTENT_REVIEW_FAILED" : error.getReason()));
    }
}
