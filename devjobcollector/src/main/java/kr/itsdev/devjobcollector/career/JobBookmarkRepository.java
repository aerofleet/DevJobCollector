package kr.itsdev.devjobcollector.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobBookmarkRepository extends JpaRepository<JobBookmark, Long> {
    @EntityGraph(attributePaths = "jobPost")
    List<JobBookmark> findAllByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = "jobPost")
    Optional<JobBookmark> findByUser_IdAndJobPost_Id(Long userId, Long jobPostId);
    boolean existsByUser_IdAndJobPost_Id(Long userId, Long jobPostId);
    long deleteByUser_IdAndJobPost_Id(Long userId, Long jobPostId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO job_bookmarks (user_id, job_post_id)
            VALUES (:userId, :jobPostId)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("jobPostId") Long jobPostId);
}
