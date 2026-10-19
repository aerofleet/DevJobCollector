package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.career.CompanyResumeService;
import kr.itsdev.devjobcollector.dto.career.ResumeDetailResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/resumes")
public class CompanyResumeController {
    private final CompanyResumeService service;
    public CompanyResumeController(CompanyResumeService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Page<ResumeSummaryResponse>> list(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(subject, companyId, page));
    }

    @GetMapping("/{resumeId}")
    public ResponseEntity<ResumeDetailResponse> get(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @PathVariable Long resumeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(subject, companyId, resumeId));
    }
}
