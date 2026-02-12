package kr.itsdev.devjobcollector.repository;

import kr.itsdev.devjobcollector.domain.JobPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

/**
 * JobPost 엔티티를 위한 리포지토리 인터페이스
 * JpaRepository를 상속받아 기본적인 CRUD 기능을 제공한다.
 */
public interface JobPostRepository extends JpaRepository<JobPost, Long> {

    /**
     * 플랫폼 출처와 원본 고유 번호를 통해 특정 공고가 이미 존재하는지 확인한다.
     * 복합 유니크 키(uk_source_original)를 활용한 조회 로직이다.
     */
    Optional<JobPost> findBySourcePlatformAndOriginalSn(String sourcePlatform, String originalSn);

    /**
     * 특정 공고의 존재 여부를 빠르게 파악한다.
     * 데이터 수집 시 중복 체크를 위해 활용된다.
     */
    boolean existsBySourcePlatformAndOriginalSn(String sourcePlatform, String originalSn);

    // 활성화된 공고만 최신순으로 페이징 조회
    Page<JobPost> findAllByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);
}
