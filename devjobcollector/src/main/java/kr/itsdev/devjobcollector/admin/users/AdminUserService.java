package kr.itsdev.devjobcollector.admin.users;

import java.time.LocalDateTime;
import java.time.Clock;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
    private final UserAccountRepository users;
    private final UserModerationHistoryRepository history;
    private final AdminAuditLogRepository audit;

    public AdminUserService(UserAccountRepository users, UserModerationHistoryRepository history,
                            AdminAuditLogRepository audit) {
        this.users = users;
        this.history = history;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(String keyword, UserAccountStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        String term = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (term != null && term.length() > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD");
        return users.searchForAdmin(term, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(UserView::from);
    }

    @Transactional(readOnly = true)
    public UserView detail(Long id) {
        return UserView.from(users.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND")));
    }

    @Transactional
    public UserView moderate(Long id, UserAccountStatus target, long expectedVersion,
                             String reason, AdminPrincipal actor, String requestId, String ipAddress,
                             String userAgent) {
        if (actor.role() == AdminRole.REVIEWER) throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        if (target != UserAccountStatus.ACTIVE && target != UserAccountStatus.SUSPENDED) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REASON");
        }
        UserAccount user = users.findByIdForModeration(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        if (user.getVersion() != expectedVersion) throw error(HttpStatus.CONFLICT, "USER_VERSION_CONFLICT");
        UserAccountStatus previous = user.getStatus();
        if (previous == target || (target == UserAccountStatus.ACTIVE
                && previous != UserAccountStatus.SUSPENDED)
                || (target == UserAccountStatus.SUSPENDED
                && previous != UserAccountStatus.ACTIVE)) {
            throw error(HttpStatus.CONFLICT, "USER_STATUS_CONFLICT");
        }
        user.moderate(target, LocalDateTime.now(Clock.systemUTC()));
        users.saveAndFlush(user);
        history.save(new UserModerationHistory(id, previous.name(), target.name(),
                reason.trim(), actor.id()));
        audit.save(AdminAuditLog.record(actor.id(), "USER_STATUS_CHANGED", "USER",
                id.toString(), reason.trim(), "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"" + target + "\"}", AdminAuditResult.SUCCESS,
                requestId, truncate(ipAddress, 45), truncate(userAgent, 500)));
        return UserView.from(user);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    public record UserView(Long id, String email, String name, String role, String status,
                           String provider, long version, LocalDateTime createdAt) {
        static UserView from(UserAccount user) {
            return new UserView(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                    user.getStatus().name(), user.getProvider().name(), user.getVersion(),
                    user.getCreatedAt());
        }
    }
}
