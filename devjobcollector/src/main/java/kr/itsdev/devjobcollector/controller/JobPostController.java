package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.dto.JobPostDetailDto;
import kr.itsdev.devjobcollector.dto.JobPostDto;
import kr.itsdev.devjobcollector.service.JobPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
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
                @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
            log.info("채용공고 목록 조회 요청 - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
            Page<JobPostDto> jobs = jobPostService.getJobPosts(pageable);
            return ResponseEntity.ok(jobs);
    }

    /**
     * 채용 공고 상세 조회
     * GET /api/v1/jobs/1
     */    
    @GetMapping("/{id}")
    public ResponseEntity<JobPostDetailDto> getJobDetail(@PathVariable("id") Long id) {
        log.info("채용공고 상세 조회 요청 - id: {}", id);
        JobPostDetailDto job = jobPostService.getJobPostDetail(id);
        return ResponseEntity.ok(job);
    }

    /**
     * 채용공고 검색
     * GET /api/v1/jobs/search?keyword=개발자&location=서울&experience=신입&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<Page<JobPostDto>> searchJobPosts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String location,
        @RequestParam(required = false) String experience,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        log.info("검색 요청 - keyword: {}, location: {}, experience: {}, page: {}, size: {}",
                keyword, location, experience, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<JobPostDto> results = jobPostService.searchJobPosts(
                keyword, location, experience, pageable);
        log.info("검색 결과: {} 건", results.getTotalElements());

        return ResponseEntity.ok(results);               
    }   

        /**
     * 활성 채용공고만 조회 (마감일이 지나지 않은 공고)
     * GET /api/v1/jobs/active?page=0&size=10
     */
    @GetMapping("/active")
    public ResponseEntity<Page<JobPostDto>> getActiveJobs(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("활성 채용공고 조회 요청 - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSizer());
        Page<JobPostDto> jobs = jobPostService.getActiveJobPosts(pageable);
        return ResponseEntity.ok(jobs);       
    }
    /**
     * 기술 스택으로 필터링
     * GET /api/v1/jobs/tech-stack?stackName=Java&page=0&size=10
     */
    @GetMapping("/tech-stack")
    public ResponseEntity<Page<JobPostDto>> getJobsByTechStack(
            @RequestParam String stackName,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("기술스택 필터링 요청 - stackName: {}, page: {}, size: {}", 
                stackName, pageable.getPageNumber(), pageable.getPageSize());
        Page<JobPostDto> jobs = jobPostService.getJobPostsByTechStack(stackName, pageable);
        return ResponseEntity.ok(jobs);
    } 
}
