package kr.itsdev.devjobcollector.career;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    @EntityGraph(attributePaths = "jobPost")
    List<JobApplication> findAllByUser_IdOrderByUpdatedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = "jobPost")
    Optional<JobApplication> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = "jobPost")
    Optional<JobApplication> findByUser_IdAndJobPost_Id(Long userId, Long jobPostId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "jobPost")
    @Query("""
            SELECT application
            FROM JobApplication application
            WHERE application.user.id = :userId AND application.jobPost.id = :jobPostId
            """)
    Optional<JobApplication> findByOwnerAndJobForUpdate(
            @Param("userId") Long userId,
            @Param("jobPostId") Long jobPostId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO applications
                (user_id, job_post_id, application_status, applied_at, memo)
            VALUES
                (:userId, :jobPostId, 'APPLIED', :appliedAt, :memo)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("jobPostId") Long jobPostId,
            @Param("appliedAt") java.time.LocalDateTime appliedAt,
            @Param("memo") String memo
    );
}
