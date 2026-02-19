package kr.itsdev.devjobcollector.repository;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * JobPost 엔티티 Repository
 * 채용 공고 데이터 접근 계층
 */
@Repository
public interface JobPostRepository extends JpaRepository<JobPost, Long> {
    
    // ===== 기본 조회 =====
    
    /**
     * 활성 공고만 조회
     */
    Page<JobPost> findByIsActiveTrue(Pageable pageable);
    
    /**
     * 특정 플랫폼의 공고 조회
     */
    Page<JobPost> findBySourcePlatform(SourcePlatform sourcePlatform, Pageable pageable);
    
    // ===== 검색 =====
    
    /**
     * 회사명으로 검색
     */
    Page<JobPost> findByCompanyNameContaining(String companyName, Pageable pageable);
    
    /**
     * 제목으로 검색
     */
    Page<JobPost> findByTitleContaining(String title, Pageable pageable);
    
    /**
     * 지역으로 검색
     */
    Page<JobPost> findByLocationContaining(String location, Pageable pageable);
    
    /**
     * 복합 검색 (제목 또는 회사명)
     */
    @Query("SELECT j FROM JobPost j WHERE " +
           "j.title LIKE %:keyword% OR j.companyName LIKE %:keyword%")
    Page<JobPost> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    // ===== 중복 체크 =====
    
    /**
     * 중복 체크 (플랫폼 + 원본 일련번호)
     */
    boolean existsBySourcePlatformAndOriginalSn(
        SourcePlatform sourcePlatform, 
        String originalSn
    );
    
    /**
     * 플랫폼 + 원본 일련번호로 조회
     */
    Optional<JobPost> findBySourcePlatformAndOriginalSn(
        SourcePlatform sourcePlatform, 
        String originalSn
    );
    
    // ===== 날짜 필터링 =====
    
    /**
     * 마감일이 특정 날짜 이후인 공고 조회
     */
    Page<JobPost> findByEndDateAfter(LocalDate date, Pageable pageable);
    
    /**
     * 마감일이 특정 날짜 이전인 공고 조회 (만료된 공고)
     */
    Page<JobPost> findByEndDateBefore(LocalDate date, Pageable pageable);
    
    /**
     * 활성 + 마감일이 지나지 않은 공고 조회
     */
    @Query("SELECT j FROM JobPost j " +
           "WHERE j.isActive = true " +
           "AND j.endDate >= :today " +
           "ORDER BY j.createdAt DESC")
    Page<JobPost> findActiveAndNotExpired(
        @Param("today") LocalDate today, 
        Pageable pageable
    );

    /**
     * 마감일이 지나지 않은 공고만 조회
     */
    @Query("SELECT j FROM JobPost j WHERE j.endDate >= :today AND j.isActive = true")
    Page<JobPost> findActiveJobPosts(@Param("today") LocalDate today, Pageable pageable);
    
    /**
     * 또는 Spring Data JPA 메서드 명명 규칙 사용
     */
    Page<JobPost> findByEndDateGreaterThanEqualAndIsActiveTrue(LocalDate today, Pageable pageable);
    
    // ===== 기술 스택 검색 =====
    
    /**
     * 특정 기술 스택을 포함하는 공고 조회
     */
    @Query("SELECT DISTINCT j FROM JobPost j " +
           "JOIN j.postTags pt " +
           "JOIN pt.techStack ts " +
           "WHERE ts.stackName = :stackName " +
           "AND j.isActive = true")
    Page<JobPost> findByTechStackName(
        @Param("stackName") String stackName, 
        Pageable pageable
    );
    
    /**
     * 여러 기술 스택 중 하나라도 포함하는 공고 조회
     */
    @Query("SELECT DISTINCT j FROM JobPost j " +
           "JOIN j.postTags pt " +
           "JOIN pt.techStack ts " +
           "WHERE ts.stackName IN :stackNames " +
           "AND j.isActive = true")
    Page<JobPost> findByTechStackNames(
        @Param("stackNames") Iterable<String> stackNames, 
        Pageable pageable
    );
    
    // ===== 통계 =====
    
    /**
     * 활성 공고 개수
     */
    long countByIsActiveTrue();
    
    /**
     * 특정 플랫폼의 공고 개수
     */
    long countBySourcePlatform(SourcePlatform sourcePlatform);
    
    /**
     * 오늘 등록된 공고 개수
     */
    @Query("SELECT COUNT(j) FROM JobPost j " +
           "WHERE DATE(j.createdAt) = :today")
    long countTodayPosts(@Param("today") LocalDate today);
}