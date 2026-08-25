package kr.itsdev.devjobcollector.controller;

import java.util.List;
import kr.itsdev.devjobcollector.career.JobBookmarkService;
import kr.itsdev.devjobcollector.dto.career.JobBookmarkResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/bookmarks")
public class JobBookmarkController {
    private final JobBookmarkService bookmarkService;

    public JobBookmarkController(JobBookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping("/{jobPostId}")
    public JobBookmarkResponse create(
            @AuthenticationPrincipal String subject,
            @PathVariable Long jobPostId
    ) {
        return bookmarkService.create(subject, jobPostId);
    }

    @GetMapping
    public List<JobBookmarkResponse> list(@AuthenticationPrincipal String subject) {
        return bookmarkService.list(subject);
    }

    @DeleteMapping("/{jobPostId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal String subject,
            @PathVariable Long jobPostId
    ) {
        bookmarkService.delete(subject, jobPostId);
        return ResponseEntity.noContent().build();
    }
}
