package kr.itsdev.devjobcollector.admin;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findByActorAdminIdOrderByOccurredAtDesc(Long actorAdminId, Pageable pageable);
    List<AdminAuditLog> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(
            String targetType, String targetId, Pageable pageable);
}
