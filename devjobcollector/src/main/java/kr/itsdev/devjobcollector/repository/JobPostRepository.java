package kr.itsdev.devjobcollector.repository;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JobPost 엔티티 Repository
 * 채용 공고 데이터 접근 계층
 */
@Repository
public interface JobPostRepository extends JpaRepository<JobPost, Long>, JobPostRepositoryCustom {
    
    // ===== 기본 조회 (무한 스크롤 대응 Slice) =====
    
    Slice<JobPost> findByIsActiveTrue(Pageable pageable);
    
    Slice<JobPost> findBySourcePlatform(SourcePlatform sourcePlatform, Pageable pageable);

    /**
     * 활성 + 마감일이 지나지 않은 공고 조회 (무한 스크롤)
     */
    @Query("SELECT j FROM JobPost j " +
           "WHERE j.isActive = true " +
           "AND j.endDate >= :today " +
           "ORDER BY j.createdAt DESC")
    Slice<JobPost> findActiveAndNotExpiredSlice(
        @Param("today") LocalDate today,
        Pageable pageable
    );
    
    // ===== 검색 =====
    
    @Query("SELECT j FROM JobPost j WHERE " +
           "j.isActive = true " +
           "AND j.endDate >= :today " +
           "AND (LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(j.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(j.location) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Slice<JobPost> searchByKeywordSlice(
        @Param("keyword") String keyword,
        @Param("today") LocalDate today,
        Pageable pageable
    );

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
    
    // ===== 기술 스택 검색 (Slice) =====
    @EntityGraph(attributePaths = {"postTags", "postTags.techStack"})
    @Query("SELECT DISTINCT j FROM JobPost j " +
           "JOIN j.postTags pt " +
           "JOIN pt.techStack ts " +
           "WHERE ts.stackName IN :stackNames " +
           "AND j.isActive = true " +
           "AND j.endDate >= :today")
    Slice<JobPost> findByTechStackNamesSlice(
        @Param("stackNames") List<String> stackNames,
        @Param("today") LocalDate today,
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
     * 일괄 중복 체크: 플랫폼 + 원본 일련번호 목록 존재 여부
     */
    @Query("SELECT j.originalSn FROM JobPost j " +
           "WHERE j.sourcePlatform = :platform " +
           "AND j.originalSn IN :originalSnList")
    List<String> findExistingOriginalSns(
        @Param("platform") SourcePlatform platform,
        @Param("originalSnList") List<String> originalSnList
    );

    /**
     * 활성 + 마감일 유효 공고 개수
     */
    @Query("SELECT COUNT(j) FROM JobPost j " +
           "WHERE j.isActive = true AND j.endDate >= :today")
    long countActiveAndValid(@Param("today") LocalDate today);

    /**
     * 오늘 등록된 공고 개수
     */
    @Query("SELECT COUNT(j) FROM JobPost j " +
           "WHERE DATE(j.createdAt) = :today")
    long countTodayPosts(@Param("today") LocalDate today);

    /**
     * 만료된 공고 비활성화
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JobPost j SET j.isActive = false WHERE j.endDate < :today AND j.isActive = true")
    int deactivateExpired(@Param("today") LocalDate today);

    /**
     * 1년 이상 지난 비활성 공고 ID 조회 (백업용)
     */
    @Query("SELECT j.id FROM JobPost j WHERE j.isActive = false AND j.endDate < :threshold")
    List<Long> findInactiveIdsOlderThan(@Param("threshold") LocalDate threshold);

    /**
     * 1년 이상 지난 비활성 공고 물리 삭제
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM JobPost j WHERE j.isActive = false AND j.endDate < :threshold")
    int deleteInactiveOlderThan(@Param("threshold") LocalDate threshold);
}
