package kr.itsdev.devjobcollector.admin.audit;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuditService {
    private final AdminAuditLogRepository logs;

    public AdminAuditService(AdminAuditLogRepository logs) { this.logs = logs; }

    @Transactional(readOnly = true)
    public Page<AuditView> search(AdminPrincipal actor, Long actorId, String action,
                                  String targetType, String targetId, AdminAuditResult result,
                                  LocalDateTime fromTime, LocalDateTime toTime,
                                  int page, int size) {
        if (actor.role() != AdminRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        }
        if (page < 0 || size < 1 || size > 100 || invalid(action, 100)
                || invalid(targetType, 50) || invalid(targetId, 100)
                || fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_FILTER");
        }
        return logs.searchForAdmin(actorId, trim(action), trim(targetType), trim(targetId),
                result, fromTime, toTime,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(AuditView::from);
    }

    private static boolean invalid(String value, int max) {
        return value != null && value.trim().length() > max;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AuditView(Long id, LocalDateTime occurredAt, Long actorAdminId,
                            String action, String targetType, String targetId,
                            String reason, String result, String requestId) {
        static AuditView from(AdminAuditLog log) {
            return new AuditView(log.getId(), log.getOccurredAt(), log.getActorAdminId(),
                    log.getAction(), log.getTargetType(), log.getTargetId(),
                    log.getReason(), log.getResult().name(), log.getRequestId());
        }
    }
}
