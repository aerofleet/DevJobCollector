package kr.itsdev.devjobcollector.admin.accounts;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import kr.itsdev.devjobcollector.admin.AdminAccount;
import kr.itsdev.devjobcollector.admin.AdminAccountRepository;
import kr.itsdev.devjobcollector.admin.AdminAccountStatus;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.AdminSessionRepository;
import kr.itsdev.devjobcollector.admin.auth.AdminMfaSecretCipher;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminTotpVerifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAccountManagementService {
    private final AdminAccountRepository accounts;
    private final AdminSessionRepository sessions;
    private final AdminAuditLogRepository audit;
    private final PasswordEncoder passwords;
    private final AdminMfaSecretCipher mfaCipher;

    public AdminAccountManagementService(AdminAccountRepository accounts,
            AdminSessionRepository sessions, AdminAuditLogRepository audit,
            PasswordEncoder passwords, AdminMfaSecretCipher mfaCipher) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.audit = audit;
        this.passwords = passwords;
        this.mfaCipher = mfaCipher;
    }

    @Transactional(readOnly = true)
    public Page<AccountView> list(AdminPrincipal actor, String keyword, AdminRole role,
                                  AdminAccountStatus status, int page, int size) {
        requireSuperAdmin(actor);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        String term = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (term != null && term.length() > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD");
        return accounts.searchForAdmin(term, role, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(AccountView::from);
    }

    @Transactional(readOnly = true)
    public AccountView detail(AdminPrincipal actor, Long id) {
        requireSuperAdmin(actor);
        return AccountView.from(accounts.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ADMIN_ACCOUNT_NOT_FOUND")));
    }

    @Transactional
    public AccountView create(AdminPrincipal actor, String email, String name,
                              AdminRole role, String password, String mfaSecret,
                              String requestId, String ipAddress, String userAgent) {
        requireSuperAdmin(actor);
        requireAssignableRole(role);
        if (email == null || email.isBlank() || name == null || name.isBlank()
                || password == null || password.length() < 16 || password.length() > 128
                || password.chars().anyMatch(Character::isISOControl)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_CREDENTIALS");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedSecret = mfaSecret == null ? "" : mfaSecret.replace(" ", "")
                .toUpperCase(Locale.ROOT);
        if (!normalizedSecret.matches("[A-Z2-7]{32}")
                || AdminTotpVerifier.decodeBase32(normalizedSecret).length != 20) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_MFA_SECRET");
        }
        if (accounts.existsByEmail(normalizedEmail)) {
            throw error(HttpStatus.CONFLICT, "ADMIN_EMAIL_EXISTS");
        }
        AdminAccount account;
        try {
            account = AdminAccount.active(normalizedEmail, passwords.encode(password), name, role);
            account.configureMfa(mfaCipher.encrypt(normalizedSecret), "admin-mfa-v1");
            account = accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "ADMIN_EMAIL_EXISTS");
        } catch (IllegalArgumentException exception) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_ACCOUNT");
        }
        record(actor, account.getId(), "ADMIN_ACCOUNT_CREATED", "initial provisioning",
                null, "{\"role\":\"" + role + "\",\"status\":\"ACTIVE\"}",
                requestId, ipAddress, userAgent);
        return AccountView.from(account);
    }

    @Transactional
    public AccountView changeStatus(AdminPrincipal actor, Long id, AdminAccountStatus target,
                                    long expectedVersion, String reason, String requestId,
                                    String ipAddress, String userAgent) {
        requireSuperAdmin(actor);
        validateReason(reason);
        AdminAccount account = mutableTarget(actor, id, expectedVersion);
        AdminAccountStatus previous = account.getStatus();
        if (target == AdminAccountStatus.DISABLED
                && (previous == AdminAccountStatus.ACTIVE || previous == AdminAccountStatus.LOCKED)) {
            account.disable();
        } else if (target == AdminAccountStatus.ACTIVE && previous == AdminAccountStatus.DISABLED) {
            account.enable();
        } else {
            throw error(HttpStatus.CONFLICT, "ADMIN_STATUS_CONFLICT");
        }
        accounts.saveAndFlush(account);
        sessions.revokeActiveByAdminId(id, LocalDateTime.now(Clock.systemUTC()));
        record(actor, id, "ADMIN_ACCOUNT_STATUS_CHANGED", reason.trim(),
                "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"" + target + "\"}", requestId, ipAddress, userAgent);
        return AccountView.from(account);
    }

    @Transactional
    public AccountView changeRole(AdminPrincipal actor, Long id, AdminRole target,
                                  long expectedVersion, String reason, String requestId,
                                  String ipAddress, String userAgent) {
        requireSuperAdmin(actor);
        requireAssignableRole(target);
        validateReason(reason);
        AdminAccount account = mutableTarget(actor, id, expectedVersion);
        AdminRole previous = account.getRole();
        if (previous == target) throw error(HttpStatus.CONFLICT, "ADMIN_ROLE_CONFLICT");
        account.changeRole(target);
        accounts.saveAndFlush(account);
        sessions.revokeActiveByAdminId(id, LocalDateTime.now(Clock.systemUTC()));
        record(actor, id, "ADMIN_ACCOUNT_ROLE_CHANGED", reason.trim(),
                "{\"role\":\"" + previous + "\"}",
                "{\"role\":\"" + target + "\"}", requestId, ipAddress, userAgent);
        return AccountView.from(account);
    }

    private AdminAccount mutableTarget(AdminPrincipal actor, Long id, long expectedVersion) {
        AdminAccount account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ADMIN_ACCOUNT_NOT_FOUND"));
        if (account.getId().equals(actor.id()) || account.getRole() == AdminRole.SUPER_ADMIN) {
            throw error(HttpStatus.FORBIDDEN, "PROTECTED_ADMIN_ACCOUNT");
        }
        if (account.getVersion() != expectedVersion) {
            throw error(HttpStatus.CONFLICT, "ADMIN_VERSION_CONFLICT");
        }
        return account;
    }

    private void record(AdminPrincipal actor, Long id, String action, String reason,
                        String before, String after, String requestId,
                        String ipAddress, String userAgent) {
        audit.save(AdminAuditLog.record(actor.id(), action, "ADMIN_ACCOUNT", id.toString(),
                reason, before, after, AdminAuditResult.SUCCESS, requestId,
                truncate(ipAddress, 45), truncate(userAgent, 500)));
    }

    private static void requireSuperAdmin(AdminPrincipal actor) {
        if (actor == null || actor.role() != AdminRole.SUPER_ADMIN) {
            throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        }
    }

    private static void requireAssignableRole(AdminRole role) {
        if (role != AdminRole.ADMIN && role != AdminRole.REVIEWER) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_ROLE");
        }
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REASON");
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    public record AccountView(Long id, String email, String name, String role,
                              String status, boolean mfaConfigured, long version,
                              LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        static AccountView from(AdminAccount account) {
            return new AccountView(account.getId(), account.getEmail(), account.getName(),
                    account.getRole().name(), account.getStatus().name(),
                    account.getMfaSecretCiphertext() != null, account.getVersion(),
                    account.getCreatedAt(), account.getLastLoginAt());
        }
    }
}
