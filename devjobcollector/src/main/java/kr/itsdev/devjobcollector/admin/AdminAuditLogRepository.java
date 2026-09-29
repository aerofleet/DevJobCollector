package kr.itsdev.devjobcollector.admin;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @Query("SELECT a FROM AdminAuditLog a WHERE (:actorId IS NULL OR a.actorAdminId = :actorId) "
            + "AND (:action IS NULL OR a.action = :action) "
            + "AND (:targetType IS NULL OR a.targetType = :targetType) "
            + "AND (:targetId IS NULL OR a.targetId = :targetId) "
            + "AND (:result IS NULL OR a.result = :result) "
            + "AND (:fromTime IS NULL OR a.occurredAt >= :fromTime) "
            + "AND (:toTime IS NULL OR a.occurredAt < :toTime)")
    Page<AdminAuditLog> searchForAdmin(@Param("actorId") Long actorId,
            @Param("action") String action, @Param("targetType") String targetType,
            @Param("targetId") String targetId, @Param("result") AdminAuditResult result,
            @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime,
            Pageable pageable);
    List<AdminAuditLog> findByActorAdminIdOrderByOccurredAtDesc(Long actorAdminId, Pageable pageable);
    List<AdminAuditLog> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(
            String targetType, String targetId, Pageable pageable);
}
