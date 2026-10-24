package kr.itsdev.devjobcollector.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.ai.LocalRecommendationEngine;
import kr.itsdev.devjobcollector.ai.ResumeAnalysisService;
import kr.itsdev.devjobcollector.ai.ResumeExplanationService;
import kr.itsdev.devjobcollector.ai.OpenAiEvidenceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/resume-analyses")
@ConditionalOnProperty(name = "ai.analysis.enabled", havingValue = "true")
public class ResumeAnalysisController {
    private final ResumeAnalysisService service;
    private final ResumeExplanationService explanations;
    private final OpenAiEvidenceClient openai;
    private final kr.itsdev.devjobcollector.ai.SavedResumeAnalysisInput savedInput;
    public ResumeAnalysisController(ResumeAnalysisService service, ResumeExplanationService explanations, OpenAiEvidenceClient openai,
                                    kr.itsdev.devjobcollector.ai.SavedResumeAnalysisInput savedInput) {
        this.service = service; this.explanations = explanations; this.openai = openai; this.savedInput = savedInput;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<?> capabilities() {
        return noStore(Map.of("mode", "LOCAL", "consentVersion", ResumeAnalysisService.CONSENT_VERSION,
                "dailyLimit", 5, "externalProcessing", openai.available(), "contentReviewAvailable", openai.available(),
                "externalConsentVersion", ResumeExplanationService.CONSENT_VERSION,
                "externalModel", OpenAiEvidenceClient.MODEL, "externalPayload", "EVIDENCE_CODES_ONLY"));
    }
    @PostMapping
    public ResponseEntity<ObjectNode> create(@AuthenticationPrincipal String subject,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request request) {
        if (!ResumeAnalysisService.CONSENT_VERSION.equals(request.consentVersion()) || !key.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_ANALYSIS_REQUEST");
        }
        if (request.careerYears() == null && request.category() == null && request.location() == null && request.employmentType() == null) {
            var previous = service.savedRetry(subject, request.resumeId(), key, request.consentVersion());
            if (previous != null) return noStore(previous);
            var prepared = savedInput.prepare(subject, request.resumeId());
            return noStore(service.createSaved(subject, request.resumeId(), key, request.consentVersion(), prepared));
        }
        return noStore(service.create(subject, request.resumeId(), key, request.consentVersion(),
                new LocalRecommendationEngine.Preferences(request.careerYears(), request.category(), request.location(), request.employmentType())));
    }
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@AuthenticationPrincipal String subject) {
        return noStore(service.list(subject));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ObjectNode> get(@AuthenticationPrincipal String subject, @PathVariable long id) {
        return noStore(service.get(subject, id));
    }
    @GetMapping("/{id}/explanation")
    public ResponseEntity<ObjectNode> explanation(@AuthenticationPrincipal String subject, @PathVariable long id) {
        return noStore(explanations.get(subject, id));
    }
    @GetMapping("/{id}/recommendations")
    public ResponseEntity<ObjectNode> currentRecommendations(@AuthenticationPrincipal String subject, @PathVariable long id) {
        return noStore(service.currentRecommendations(subject, id));
    }
    @PutMapping("/{id}/recommendations/feedback")
    public ResponseEntity<Void> currentRecommendationFeedback(@AuthenticationPrincipal String subject, @PathVariable long id,
            @Valid @RequestBody Feedback request) {
        service.currentRecommendationFeedback(subject, id, request.jobId(), request.type());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/{id}/explanation")
    public ResponseEntity<ObjectNode> explain(@AuthenticationPrincipal String subject, @PathVariable long id,
                                            @Valid @RequestBody ExplanationRequest request) {
        return noStore(explanations.explain(subject, id, request.consentVersion()));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String subject, @PathVariable long id) {
        service.delete(subject, id); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PutMapping("/{id}/feedback")
    public ResponseEntity<Void> feedback(@AuthenticationPrincipal String subject, @PathVariable long id,
                                        @Valid @RequestBody Feedback request) {
        service.feedback(subject, id, request.jobId(), request.type());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PutMapping("/{id}/findings/{findingId}/feedback")
    public ResponseEntity<Void> findingFeedback(@AuthenticationPrincipal String subject, @PathVariable long id,
            @PathVariable String findingId, @Valid @RequestBody FindingFeedback request) {
        service.findingFeedback(subject, id, findingId, request.type());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    public record FindingFeedback(@NotBlank @Pattern(regexp = "OPEN|APPLIED|NOT_APPLICABLE|INCORRECT") String type) {}
    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
    @ExceptionHandler(kr.itsdev.devjobcollector.ai.SavedResumeAnalysisInput.AttachmentReadFailure.class)
    public ResponseEntity<?> attachmentFailure(kr.itsdev.devjobcollector.ai.SavedResumeAnalysisInput.AttachmentReadFailure error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("code", "ATTACHMENT_READ_FAILED", "attachmentId", error.attachmentId()));
    }
    public record Request(@NotNull @Positive Long resumeId, @NotBlank @Size(max = 50) String consentVersion,
                          @Min(0) @Max(60) Integer careerYears,
                          @Pattern(regexp = "|백엔드|프론트엔드|풀스택|데이터|인공지능|모바일|보안|DevOps") String category,
                          @Pattern(regexp = "|서울|경기|인천|부산|대구|대전|광주|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주|원격") String location,
                          @Pattern(regexp = "|정규직|계약직|인턴|프리랜서") String employmentType) {}
    public record Feedback(@NotNull @Positive Long jobId, @NotBlank @Size(max = 40) String type) {}
    public record ExplanationRequest(@NotBlank @Size(max = 50) String consentVersion) {}
}
