package kr.itsdev.devjobcollector.controller;

import java.util.List;
import kr.itsdev.devjobcollector.career.JobApplicationService;
import kr.itsdev.devjobcollector.dto.career.JobApplicationCreateRequest;
import kr.itsdev.devjobcollector.dto.career.JobApplicationResponse;
import kr.itsdev.devjobcollector.dto.career.JobApplicationStatusUpdateRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/applications")
public class JobApplicationController {
    private final JobApplicationService applicationService;

    public JobApplicationController(JobApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/{jobPostId}")
    public JobApplicationResponse create(
            @AuthenticationPrincipal String subject,
            @PathVariable Long jobPostId,
            @RequestBody(required = false) JobApplicationCreateRequest request
    ) {
        return applicationService.create(subject, jobPostId, request);
    }

    @GetMapping
    public List<JobApplicationResponse> list(@AuthenticationPrincipal String subject) {
        return applicationService.list(subject);
    }

    @PatchMapping("/{applicationId}/status")
    public JobApplicationResponse changeStatus(
            @AuthenticationPrincipal String subject,
            @PathVariable Long applicationId,
            @RequestBody JobApplicationStatusUpdateRequest request
    ) {
        return applicationService.changeStatus(subject, applicationId, request);
    }
}
