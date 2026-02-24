package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.dto.JobPostDto;
import kr.itsdev.devjobcollector.dto.TechStackDto;
import kr.itsdev.devjobcollector.dto.JobPostDetailDto;
import kr.itsdev.devjobcollector.dto.JobFileDto;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostService {

    private final JobPostRepository jobPostRepository;

    /**
     * 채용 공고 목록 조회 (페이징)
     */
    @Transactional(readOnly = false)
    public Page<JobPostDto> getJobPosts(Pageable pageable) {
        log.info("채용 공고 목록 조회: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        deactivateExpiredPosts();

        LocalDate today = LocalDate.now();
        return jobPostRepository.findActiveJobPosts(today, pageable)
                .map(this::convertToDto);
    }

    /**
     * 통합 검색 (모든 텍스트 필드 + 기술스택)
     * - keyword 없으면 활성 공고 전체 반환
     * - 마감일 지난 공고 제외
     */
    @Transactional(readOnly = false)
    public Page<JobPostDto> searchJobPosts(String keyword, String location, String experience, Pageable pageable) {
        log.info("채용 공고 검색: keyword={}, location={}, experience={}, page={}, size={}",
                keyword, location, experience, pageable.getPageNumber(), pageable.getPageSize());

        LocalDate today = LocalDate.now();
        deactivateExpiredPosts();
        Page<JobPost> results;

        if (keyword != null && !keyword.isBlank()) {
            results = jobPostRepository.searchByAllFields(keyword, today, pageable);
        } else {
            // 키워드 없으면 활성/유효 공고 전체
            results = jobPostRepository.findActiveJobPosts(today, pageable);
        }

        return results.map(this::convertToDto);
    }

    /**
     * 활성 공고 조회
     */
    public Page<JobPostDto> getActiveJobPosts(Pageable pageable) {
        log.info("활성 채용 공고 조회: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        LocalDate today = LocalDate.now();
        return jobPostRepository.findActiveJobPosts(today, pageable)
                .map(this::convertToDto);
    }

    /**
     * 기술 스택 기준 조회
     */
    public Page<JobPostDto> getJobPostsByTechStack(String stackName, Pageable pageable) {
        log.info("기술 스택 기준 조회: stackName={}, page={}, size={}", stackName, pageable.getPageNumber(), pageable.getPageSize());
        return jobPostRepository.findByTechStackName(stackName, pageable)
                .map(this::convertToDto);
    }

    /**
     * 채용 공고 상세 조회
     */
    @SuppressWarnings("null") 
    public JobPostDetailDto getJobPostDetail(Long id) {
        log.info("채용 공고 상세 조회: id={}", id);
        JobPost jobPost = jobPostRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("채용 공고를 찾을 수 없습니다. ID: " + id));
        
        return convertToDetailDto(jobPost);
    }

    /**
     * Entity → DTO 변환 (목록용)
     */
    private JobPostDto convertToDto(JobPost jobPost) {
        return JobPostDto.builder()
            .id(jobPost.getId())
            .sourcePlatform(jobPost.getSourcePlatform().name())
            .companyName(jobPost.getCompanyName())
            .title(jobPost.getTitle())
            .jobCategory(jobPost.getJobCategory())
            .experience(jobPost.getExperience())
            .location(jobPost.getLocation())
            .hireType(jobPost.getHireType())
            .startDate(jobPost.getStartDate())
            .endDate(jobPost.getEndDate())
            .originalUrl(jobPost.getOriginalUrl())
            .isActive(jobPost.isActive())
            .techStacks(convertTechStacks(jobPost))
            .build();
    }

    /**
     * Entity → DetailDTO 변환 (상세용)
     */
    private JobPostDetailDto convertToDetailDto(JobPost jobPost) {
        return JobPostDetailDto.builder()
            .id(jobPost.getId())
            .sourcePlatform(jobPost.getSourcePlatform().name())
            .companyName(jobPost.getCompanyName())
            .title(jobPost.getTitle())
            .jobCategory(jobPost.getJobCategory())
            .experience(jobPost.getExperience())
            .location(jobPost.getLocation())
            .hireType(jobPost.getHireType())
            .startDate(jobPost.getStartDate())
            .endDate(jobPost.getEndDate())
            .createdAt(jobPost.getCreatedAt())
            .originalUrl(jobPost.getOriginalUrl())
            .applyQual(jobPost.getApplyQual())
            .processInfo(jobPost.getProcessInfo())
            .isActive(jobPost.isActive())
            .techStacks(convertTechStacks(jobPost))
            .files(extractFiles(jobPost))
            .build();
    }

    /**
     * 기술 스택 추출
     */
    private List<TechStackDto> convertTechStacks(JobPost jobPost) {
        if (jobPost.getPostTags() == null || jobPost.getPostTags().isEmpty()) {
            return List.of();
        }

        return jobPost.getPostTags().stream()
            .filter(pt -> pt.getTechStack() != null)
            .map(postTag -> TechStackDto.builder()
                .id(postTag.getTechStack().getId())
                .stackName(postTag.getTechStack().getStackName())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 파일 목록 추출
     */
    private List<JobFileDto> extractFiles(JobPost jobPost) {
        if (jobPost.getFiles() == null || jobPost.getFiles().isEmpty()) {
            return List.of();
        }
        
        return jobPost.getFiles().stream()
            .map(file -> JobFileDto.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileUrl(file.getFileUrl())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 만료된 공고를 비활성화
     */
    @Transactional(readOnly = false)
    public void deactivateExpiredPosts() {
        LocalDate today = LocalDate.now();
        int updated = jobPostRepository.deactivateExpired(today);
        if (updated > 0) {
            log.info("만료된 공고 {}건 비활성화 완료 (기준일: {})", updated, today);
        }
    }

    /**
     * 매일 00:05 만료 공고 비활성화
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional(readOnly = false)
    public void scheduledDeactivateExpiredPosts() {
        deactivateExpiredPosts();
    }

    /**
     * 매일 00:30 1년 이상된 비활성 공고 백업 로그 후 물리 삭제
     */
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional(readOnly = false)
    public void purgeInactiveOlderThanOneYear() {
        LocalDate threshold = LocalDate.now().minusYears(1);
        List<Long> backupIds = jobPostRepository.findInactiveIdsOlderThan(threshold);
        if (!backupIds.isEmpty()) {
            // 간단 백업: ID 목록 로그 (필요 시 외부 백업 스토리지 연동)
            List<Long> preview = backupIds.size() > 50 ? backupIds.subList(0, 50) : backupIds;
            log.info("백업 대상 비활성 공고 {}건 (표본 50개): {}", backupIds.size(), preview);
        }

        int deleted = jobPostRepository.deleteInactiveOlderThan(threshold);
        if (deleted > 0) {
            log.info("1년 초과 비활성 공고 {}건 물리 삭제 완료 (기준일: {})", deleted, threshold);
        }
    }
}
