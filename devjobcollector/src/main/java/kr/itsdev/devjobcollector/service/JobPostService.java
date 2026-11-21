package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.dto.JobPostDto;
import kr.itsdev.devjobcollector.dto.TechStackDto;
import kr.itsdev.devjobcollector.dto.JobPostDetailDto;
import kr.itsdev.devjobcollector.dto.JobFileDto;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.monitoring.JobSearchMetrics;
import kr.itsdev.devjobcollector.ai.JobRoleAiReadService;
import kr.itsdev.devjobcollector.ai.JobFieldAiReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostService {

    private final JobPostRepository jobPostRepository;
    private final JobSearchMetrics jobSearchMetrics;
    private final JobRoleAiReadService jobRoleAiReadService;
    private final JobFieldAiReadService jobFieldAiReadService;

    /**
     * 채용 공고 목록 조회 (페이징)
     */
    @SuppressWarnings("null") // Stream.toList nullness noise from JDT
    public Page<JobPostDto> getJobPosts(Pageable pageable) {
        log.info("채용 공고 목록 조회: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        LocalDate today = LocalDate.now();
        Slice<JobPost> slice = jobPostRepository.findActiveAndNotExpiredSlice(today, pageable);
        long total = jobPostRepository.countActiveAndValid(today);
        Map<Long, String> approvedRoles = jobRoleAiReadService.approvedRoles(slice.getContent());
        Map<Long, List<String>> approvedFields = jobFieldAiReadService.approvedFields(slice.getContent());
        List<JobPostDto> content = slice.getContent().stream()
                .map(jobPost -> convertToDto(jobPost, null, approvedRoles.get(jobPost.getId()), approvedFields.get(jobPost.getId())))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 통합 검색 (모든 텍스트 필드 + 기술스택)
     * - keyword 없으면 활성 공고 전체 반환
     * - 마감일 지난 공고 제외
     */
    @SuppressWarnings("null") // Stream.toList nullness noise from JDT
    public Page<JobPostDto> searchJobPosts(
            String keyword,
            String location,
            String experience,
            String jobCategory,
            String techStackName,
            List<String> companies,
            Pageable pageable
    ) {
        log.info("채용 공고 검색: keyword={}, location={}, experience={}, jobCategory={}, techStack={}, page={}, size={}",
                keyword, location, experience, jobCategory, techStackName,
                pageable.getPageNumber(), pageable.getPageSize());

        LocalDate today = LocalDate.now();
        return jobSearchMetrics.record(keyword, () -> {
            Page<JobPost> found = jobPostRepository.searchByAllFieldsOptimized(
                    keyword, location, experience, jobCategory, techStackName, companies, today, pageable);
            Map<Long, String> approvedRoles = jobRoleAiReadService.approvedRoles(found.getContent());
            Map<Long, List<String>> approvedFields = jobFieldAiReadService.approvedFields(found.getContent());
            return found.map(jobPost -> convertToDto(jobPost, keyword, approvedRoles.get(jobPost.getId()), approvedFields.get(jobPost.getId())));
        });
    }

    public List<String> findCompanyNames(String query) {
        return jobPostRepository.findCompanyNames(query, LocalDate.now());
    }

    /**
     * 활성 공고 조회
     */
    @SuppressWarnings("null") // Stream.toList nullness noise from JDT
    public Page<JobPostDto> getActiveJobPosts(Pageable pageable) {
        log.info("활성 채용 공고 조회: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        LocalDate today = LocalDate.now();
        Slice<JobPost> slice = jobPostRepository.findActiveAndNotExpiredSlice(today, pageable);
        long total = jobPostRepository.countActiveAndValid(today);
        Map<Long, String> approvedRoles = jobRoleAiReadService.approvedRoles(slice.getContent());
        Map<Long, List<String>> approvedFields = jobFieldAiReadService.approvedFields(slice.getContent());
        List<JobPostDto> content = slice.getContent().stream()
                .map(jobPost -> convertToDto(jobPost, null, approvedRoles.get(jobPost.getId()), approvedFields.get(jobPost.getId())))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 기술 스택 기준 조회
     */
    public Page<JobPostDto> getJobPostsByTechStack(String stackName, Pageable pageable) {
        log.info("기술 스택 기준 조회: stackName={}, page={}, size={}", stackName, pageable.getPageNumber(), pageable.getPageSize());
        LocalDate today = LocalDate.now();
        Page<JobPost> found = jobPostRepository.findByTechStackNamesOptimized(List.of(stackName), today, pageable);
        Map<Long, String> approvedRoles = jobRoleAiReadService.approvedRoles(found.getContent());
        Map<Long, List<String>> approvedFields = jobFieldAiReadService.approvedFields(found.getContent());
        return found.map(jobPost -> convertToDto(jobPost, null, approvedRoles.get(jobPost.getId()), approvedFields.get(jobPost.getId())));
    }

    /**
     * 채용 공고 상세 조회
     */
    @SuppressWarnings("null") 
    public JobPostDetailDto getJobPostDetail(Long id) {
        log.info("채용 공고 상세 조회: id={}", id);
        JobPost jobPost = jobPostRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("채용 공고를 찾을 수 없습니다. ID: " + id));
        if (jobPost.getModerationStatus() != JobModerationStatus.ACTIVE
                || !jobPost.isActive() || jobPost.getEndDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "JOB_NOT_AVAILABLE");
        }
        
        String approvedRole = jobRoleAiReadService.approvedRoles(List.of(jobPost)).get(jobPost.getId());
        var approvedFields = jobFieldAiReadService.approvedFields(List.of(jobPost)).get(jobPost.getId());
        return convertToDetailDto(jobPost, approvedRole, approvedFields);
    }

    /**
     * Entity → DTO 변환 (목록용)
     */
    private JobPostDto convertToDto(JobPost jobPost, String keyword, String approvedRole, List<String> approvedFields) {
        return JobPostDto.builder()
            .id(jobPost.getId())
            .sourcePlatform(jobPost.getSourcePlatform().name())
            .companyName(jobPost.getCompanyName())
            .title(jobPost.getTitle())
            .jobCategory(jobPost.getJobCategory())
            .approvedJobRole(approvedRole)
            .approvedJobFields(approvedFields)
            .experience(jobPost.getExperienceLabel())
            .location(jobPost.getDisplayLocation())
            .hireType(jobPost.getHireType())
            .industrialPersonnelRecruitment(JobRecruitmentType.industrialPersonnel(jobPost.getTitle(), jobPost.getApplyQual()))
            .startDate(jobPost.getStartDate())
            .endDate(jobPost.getEndDate())
            .sourceDeadlineDisplay(jobPost.getSourceDeadlineDisplay())
            .originalUrl(jobPost.getOriginalUrl())
            .isActive(jobPost.isActive())
            .techStacks(convertTechStacks(jobPost))
            .mentionedTechStacks(JobMentionedTechnologies.from(jobPost.getApplyQual()))
            .matchedSnippet(JobSearchSnippet.from(jobPost, keyword))
            .build();
    }

    /**
     * Entity → DetailDTO 변환 (상세용)
     */
    private JobPostDetailDto convertToDetailDto(JobPost jobPost, String approvedRole, List<String> approvedFields) {
        return JobPostDetailDto.builder()
            .id(jobPost.getId())
            .sourcePlatform(jobPost.getSourcePlatform().name())
            .companyName(jobPost.getCompanyName())
            .title(jobPost.getTitle())
            .jobCategory(jobPost.getJobCategory())
            .approvedJobRole(approvedRole)
            .approvedJobFields(approvedFields)
            .experience(jobPost.getExperienceLabel())
            .location(jobPost.getDisplayLocation())
            .hireType(jobPost.getHireType())
            .industrialPersonnelRecruitment(JobRecruitmentType.industrialPersonnel(jobPost.getTitle(), jobPost.getApplyQual()))
            .startDate(jobPost.getStartDate())
            .endDate(jobPost.getEndDate())
            .sourceDeadlineDisplay(jobPost.getSourceDeadlineDisplay())
            .createdAt(jobPost.getCreatedAt())
            .deadlineEvidence(JobDeadlineEvidence.resolve(jobPost.getEndDate(), jobPost.getApplyQual()))
            .originalUrl(jobPost.getOriginalUrl())
            .applyQual(jobPost.getApplyQual())
            .supplementalSalaryText(jobPost.getSupplementalSalaryText())
            .supplementalSalarySourceUrl(jobPost.getSupplementalSalarySourceUrl())
            .supplementalSalaryCheckedAt(jobPost.getSupplementalSalaryCheckedAt())
            .processInfo(jobPost.getProcessInfo())
            .publicPreference(jobPost.getPublicPreference())
            .publicPreferenceConditions(jobPost.getPublicPreferenceConditions())
            .publicDisqualification(jobPost.getPublicDisqualification())
            .isActive(jobPost.isActive())
            .techStacks(convertTechStacks(jobPost))
            .files(extractFiles(jobPost))
            .contentImageUrls(jobPost.getContentImages().stream().map(kr.itsdev.devjobcollector.domain.JobPostImage::getImageUrl).toList())
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

}
