package kr.itsdev.devjobcollector.admin.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import kr.itsdev.devjobcollector.admin.AdminAccount;
import kr.itsdev.devjobcollector.admin.AdminAccountRepository;
import kr.itsdev.devjobcollector.admin.AdminAccountStatus;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminSession;
import kr.itsdev.devjobcollector.admin.AdminSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthenticationService {
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$wIClx9TYSmwdiZGau8XLs.F5/rtyGDB3xjS1vwcFQ6vU7xv4mRrLG";

    private final AdminAccountRepository accountRepository;
    private final AdminSessionRepository sessionRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminMfaSecretCipher mfaSecretCipher;
    private final AdminTotpVerifier totpVerifier;
    private final AdminTokenCodec tokenCodec;
    private final AdminSecurityProperties properties;
    private final Clock clock;

    public AdminAuthenticationService(AdminAccountRepository accountRepository,
                                      AdminSessionRepository sessionRepository,
                                      AdminAuditLogRepository auditLogRepository,
                                      PasswordEncoder passwordEncoder,
                                      AdminMfaSecretCipher mfaSecretCipher,
                                      AdminTotpVerifier totpVerifier,
                                      AdminTokenCodec tokenCodec,
                                      AdminSecurityProperties properties) {
        this(accountRepository, sessionRepository, auditLogRepository, passwordEncoder,
                mfaSecretCipher, totpVerifier, tokenCodec, properties, Clock.systemUTC());
    }

    AdminAuthenticationService(AdminAccountRepository accountRepository,
                               AdminSessionRepository sessionRepository,
                               AdminAuditLogRepository auditLogRepository,
                               PasswordEncoder passwordEncoder,
                               AdminMfaSecretCipher mfaSecretCipher,
                               AdminTotpVerifier totpVerifier,
                               AdminTokenCodec tokenCodec,
                               AdminSecurityProperties properties, Clock clock) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaSecretCipher = mfaSecretCipher;
        this.totpVerifier = totpVerifier;
        this.tokenCodec = tokenCodec;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AdminLoginResult login(String email, String password, String mfaCode,
                                  String requestId, String ipAddress, String userAgent) {
        LocalDateTime now = LocalDateTime.now(clock);
        String normalizedEmail = normalizeEmail(email);
        AdminAccount account = accountRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
        if (account == null) {
            passwordEncoder.matches(password == null ? "" : password, DUMMY_PASSWORD_HASH);
            audit(null, "ADMIN_LOGIN_FAILURE", normalizedEmail, requestId, ipAddress, userAgent,
                    AdminAuditResult.DENIED);
            return AdminLoginResult.failure(AdminLoginResult.Status.INVALID_CREDENTIALS);
        }
        if (account.getStatus() == AdminAccountStatus.DISABLED) {
            audit(account.getId(), "ADMIN_LOGIN_DISABLED", account.getId().toString(), requestId,
                    ipAddress, userAgent, AdminAuditResult.DENIED);
            return AdminLoginResult.failure(AdminLoginResult.Status.DISABLED);
        }
        if (account.isLockedAt(now)) {
            audit(account.getId(), "ADMIN_LOGIN_LOCKED", account.getId().toString(), requestId,
                    ipAddress, userAgent, AdminAuditResult.DENIED);
            return AdminLoginResult.failure(AdminLoginResult.Status.LOCKED);
        }
        if (account.getMfaSecretCiphertext() == null) {
            audit(account.getId(), "ADMIN_LOGIN_MFA_NOT_CONFIGURED", account.getId().toString(),
                    requestId, ipAddress, userAgent, AdminAuditResult.DENIED);
            return AdminLoginResult.failure(AdminLoginResult.Status.MFA_NOT_CONFIGURED);
        }

        boolean passwordValid = passwordEncoder.matches(password == null ? "" : password,
                account.getPasswordHash());
        boolean mfaValid = false;
        if (passwordValid) {
            String secret = mfaSecretCipher.decrypt(account.getMfaSecretCiphertext());
            mfaValid = totpVerifier.verify(secret, mfaCode);
        }
        if (!passwordValid || !mfaValid) {
            account.recordFailedLogin(now, properties.getMaximumFailedAttempts(),
                    properties.getLockDuration());
            AdminLoginResult.Status status = account.isLockedAt(now)
                    ? AdminLoginResult.Status.LOCKED
                    : AdminLoginResult.Status.INVALID_CREDENTIALS;
            audit(account.getId(), "ADMIN_LOGIN_FAILURE", account.getId().toString(), requestId,
                    ipAddress, userAgent, AdminAuditResult.DENIED);
            return AdminLoginResult.failure(status);
        }

        account.recordSuccessfulLogin(now);
        String sessionToken = tokenCodec.generate();
        String csrfToken = tokenCodec.generate();
        AdminSession session = sessionRepository.save(AdminSession.issue(
                account, tokenCodec.hash(sessionToken), tokenCodec.hash(csrfToken), now,
                now.plus(properties.getSessionDuration())));
        audit(account.getId(), "ADMIN_LOGIN_SUCCESS", account.getId().toString(), requestId,
                ipAddress, userAgent, AdminAuditResult.SUCCESS);
        return AdminLoginResult.success(sessionToken, csrfToken, principal(account, session));
    }

    @Transactional(readOnly = true)
    public AdminPrincipal authenticate(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) return null;
        LocalDateTime now = LocalDateTime.now(clock);
        return sessionRepository.findByRefreshHash(tokenCodec.hash(sessionToken))
                .filter(session -> session.isUsableAt(now))
                .map(session -> {
                    AdminAccount account = session.getAdmin();
                    if (account.getStatus() != AdminAccountStatus.ACTIVE) return null;
                    return principal(account, session);
                }).orElse(null);
    }

    @Transactional
    public void logout(String sessionToken, String requestId, String ipAddress, String userAgent) {
        if (sessionToken == null || sessionToken.isBlank()) return;
        sessionRepository.findByRefreshHash(tokenCodec.hash(sessionToken)).ifPresent(session -> {
            session.revoke(LocalDateTime.now(clock));
            audit(session.getAdmin().getId(), "ADMIN_LOGOUT", session.getAdmin().getId().toString(),
                    requestId, ipAddress, userAgent, AdminAuditResult.SUCCESS);
        });
    }

    private AdminPrincipal principal(AdminAccount account, AdminSession session) {
        return new AdminPrincipal(account.getId(), account.getEmail(), account.getName(),
                account.getRole(), account.getPermissions(), session.getId(), session.getCsrfHash());
    }

    private void audit(Long actorId, String action, String targetId, String requestId,
                       String ipAddress, String userAgent, AdminAuditResult result) {
        auditLogRepository.save(AdminAuditLog.record(actorId, action, "ADMIN_ACCOUNT",
                truncate(targetId, 100), null, null, null, result, requestId,
                truncate(ipAddress, 45), truncate(userAgent, 500)));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value : value.substring(0, maximumLength);
    }
}
