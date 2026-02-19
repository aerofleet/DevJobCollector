package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.dto.JobPostDetailDto;
import kr.itsdev.devjobcollector.dto.JobPostDto;
import kr.itsdev.devjobcollector.service.JobPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jobs")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class JobPostController {

    private final JobPostService jobPostService;

    /**
     * 채용 공고 목록 조회 (페이징)
     * GET /api/v1/jobs?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<JobPostDto>> getAllJobs(
                @PageableDefault(size = 10) Pageable pageable) {
            Page<JobPostDto> jobs = jobPostService.getJobPosts(pageable);
            return ResponseEntity.ok(jobs);
    }

    /**
     * 채용 공고 상세 조회
     * GET /api/v1/jobs/1
     */    
    @GetMapping("/{id}")
    public ResponseEntity<JobPostDetailDto> getJobDetail(@PathVariable("id") Long id) {
        JobPostDetailDto job = jobPostService.getJobPostDetail(id);
        return ResponseEntity.ok(job);
    }
}
