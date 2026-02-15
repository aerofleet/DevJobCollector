package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.dto.JobPostDto;
import kr.itsdev.devjobcollector.dto.JobPostDetailDto;
import kr.itsdev.devjobcollector.dto.JobFileDto;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<JobPostDto> getJobPosts(Pageable pageable) {
        log.info("채용 공고 목록 조회: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return jobPostRepository.findAll(pageable)
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
            .location(jobPost.getLocation())
            .hireType(jobPost.getHireType())
            .startDate(jobPost.getStartDate())
            .endDate(jobPost.getEndDate())
            .originalUrl(jobPost.getOriginalUrl())
            .isActive(jobPost.isActive())
            .techStacks(extractTechStacks(jobPost))
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
            .location(jobPost.getLocation())
            .hireType(jobPost.getHireType())
            .startDate(jobPost.getStartDate())
            .endDate(jobPost.getEndDate())
            .createdAt(jobPost.getCreatedAt())
            .originalUrl(jobPost.getOriginalUrl())
            .applyQual(jobPost.getApplyQual())
            .processInfo(jobPost.getProcessInfo())
            .isActive(jobPost.isActive())
            .techStacks(extractTechStacks(jobPost))
            .files(extractFiles(jobPost))
            .build();
    }

    /**
     * 기술 스택 추출
     */
    private List<String> extractTechStacks(JobPost jobPost) {
        if (jobPost.getPostTags() == null || jobPost.getPostTags().isEmpty()) {
            return List.of();
        }
        
        return jobPost.getPostTags().stream()
            .map(postTag -> postTag.getTechStack().getStackName())
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
}