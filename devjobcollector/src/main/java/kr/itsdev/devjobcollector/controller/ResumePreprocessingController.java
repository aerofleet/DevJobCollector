package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import kr.itsdev.devjobcollector.ai.ResumePiiGate;
import kr.itsdev.devjobcollector.ai.ResumePreprocessingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/resume-previews")
@ConditionalOnProperty(name = "ai.preprocessing.enabled", havingValue = "true")
public class ResumePreprocessingController {
    private final ResumePreprocessingService preprocessing;

    public ResumePreprocessingController(ResumePreprocessingService preprocessing) {
        this.preprocessing = preprocessing;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResumePiiGate.Result> fromSavedResume(
            @AuthenticationPrincipal String subject, @Valid @RequestBody PreviewRequest request) {
        return response(preprocessing.preview(subject, request.resumeId(), request.localProcessingConsent(), null));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumePiiGate.Result> fromUpload(
            @AuthenticationPrincipal String subject, @RequestParam Long resumeId,
            @RequestParam boolean localProcessingConsent, @RequestPart MultipartFile file) {
        if (resumeId <= 0) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_RESUME_ID");
        return response(preprocessing.preview(subject, resumeId, localProcessingConsent, file));
    }

    private ResponseEntity<ResumePiiGate.Result> response(ResumePiiGate.Result body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(body);
    }

    public record PreviewRequest(@NotNull @Positive Long resumeId, @AssertTrue boolean localProcessingConsent) {}
}
