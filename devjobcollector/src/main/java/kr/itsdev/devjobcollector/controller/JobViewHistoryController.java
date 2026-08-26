package kr.itsdev.devjobcollector.controller;

import java.util.List;
import kr.itsdev.devjobcollector.career.JobViewHistoryService;
import kr.itsdev.devjobcollector.dto.career.JobViewHistoryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/recent-jobs")
public class JobViewHistoryController {
    private final JobViewHistoryService viewHistoryService;

    public JobViewHistoryController(JobViewHistoryService viewHistoryService) {
        this.viewHistoryService = viewHistoryService;
    }

    @PostMapping("/{jobPostId}")
    public JobViewHistoryResponse record(
            @AuthenticationPrincipal String subject,
            @PathVariable Long jobPostId
    ) {
        return viewHistoryService.record(subject, jobPostId);
    }

    @GetMapping
    public List<JobViewHistoryResponse> list(@AuthenticationPrincipal String subject) {
        return viewHistoryService.list(subject);
    }
}
