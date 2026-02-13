package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.dto.JobPostDto;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본은 읽기 전용
public class JobPostService {

    private final JobPostRepository jobPostRepository;

    /**
     * 공고 목록 조회 (활성화된 공고 위주, 최신순)
     */
    public Page<JobPost> getJobPosts(Pageable pageable) {
        return jobPostRepository.findAllByIsActiveTrueOrderByCreatedAtDesc(pageable);        
    }

    /**
     * 공고 상세 조회
     */
    public JobPost getJobPostDetail(@NonNull Long id) {
        return jobPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공고입니다. ID: " + id));
    }

    /**
     * 공고 데이터 저장 (Enum 적용)
     * 클래스 레벨의 readOnly를 덮어쓰기 위해 @Transactional 추가
     */
    @Transactional 
    public Long saveJobPost(JobPostDto dto) { // DTO는 프로젝트 상황에 맞게 조정
        JobPost jobPost = JobPost.builder()
                .title(dto.getTitle())
                .companyName(dto.getCompanyName())
                .location(dto.getLocation())
                .sourcePlatform(SourcePlatform.valueOf(dto.getSourcePlatform()))
                .isActive(true)
                .build();

        JobPost savedPost = jobPostRepository.save(Objects.requireNonNull(jobPost));
        return Objects.requireNonNull(savedPost, "저장 실패").getId();
    }
}