package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.itsdev.devjobcollector.career.ResumeService;
import kr.itsdev.devjobcollector.dto.career.ResumeDetailResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeStatusUpdateRequest;
import kr.itsdev.devjobcollector.dto.career.ResumeSummaryResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeUpsertRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public List<ResumeSummaryResponse> list(@AuthenticationPrincipal String subject) {
        return resumeService.list(subject);
    }

    @PostMapping
    public ResponseEntity<ResumeDetailResponse> create(
            @AuthenticationPrincipal String subject,
            @Valid @RequestBody ResumeUpsertRequest request
    ) {
        ResumeDetailResponse response = resumeService.create(subject, request);
        return ResponseEntity.created(URI.create("/api/v1/members/me/resumes/" + response.id()))
                .body(response);
    }

    @GetMapping("/{resumeId}")
    public ResumeDetailResponse get(
            @AuthenticationPrincipal String subject,
            @PathVariable Long resumeId
    ) {
        return resumeService.get(subject, resumeId);
    }

    @PutMapping("/{resumeId}")
    public ResumeDetailResponse update(
            @AuthenticationPrincipal String subject,
            @PathVariable Long resumeId,
            @Valid @RequestBody ResumeUpsertRequest request
    ) {
        return resumeService.update(subject, resumeId, request);
    }

    @PatchMapping("/{resumeId}/status")
    public ResumeDetailResponse changeStatus(
            @AuthenticationPrincipal String subject,
            @PathVariable Long resumeId,
            @Valid @RequestBody ResumeStatusUpdateRequest request
    ) {
        return resumeService.changeStatus(subject, resumeId, request);
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal String subject,
            @PathVariable Long resumeId
    ) {
        resumeService.delete(subject, resumeId);
        return ResponseEntity.noContent().build();
    }
}
