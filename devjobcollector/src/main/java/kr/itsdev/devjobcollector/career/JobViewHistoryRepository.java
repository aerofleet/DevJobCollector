package kr.itsdev.devjobcollector.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobViewHistoryRepository extends JpaRepository<JobViewHistory, Long> {
    @EntityGraph(attributePaths = "jobPost")
    List<JobViewHistory> findTop100ByUser_IdOrderByLastViewedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = "jobPost")
    Optional<JobViewHistory> findByUser_IdAndJobPost_Id(Long userId, Long jobPostId);

    @Query("""
            SELECT history.id
            FROM JobViewHistory history
            WHERE history.user.id = :userId
            ORDER BY history.lastViewedAt DESC, history.id DESC
            """)
    List<Long> findIdsByUserIdOrderByMostRecent(@Param("userId") Long userId);

    @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockUserById(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO job_view_history
                (user_id, job_post_id, first_viewed_at, last_viewed_at, view_count)
            VALUES
                (:userId, :jobPostId, :viewedAt, :viewedAt, 1)
            ON DUPLICATE KEY UPDATE
                last_viewed_at = GREATEST(last_viewed_at, VALUES(last_viewed_at)),
                view_count = LEAST(view_count + 1, 2147483647)
            """, nativeQuery = true)
    int recordView(
            @Param("userId") Long userId,
            @Param("jobPostId") Long jobPostId,
            @Param("viewedAt") java.time.LocalDateTime viewedAt
    );
}
