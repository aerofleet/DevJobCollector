package kr.itsdev.devjobcollector.admin.bootstrap;

import java.nio.CharBuffer;
import java.util.Locale;
import java.util.UUID;
import kr.itsdev.devjobcollector.admin.AdminAccount;
import kr.itsdev.devjobcollector.admin.AdminAccountRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminMfaSecretCipher;
import kr.itsdev.devjobcollector.admin.auth.AdminTotpVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
    private static final int MINIMUM_PASSWORD_LENGTH = 16;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;

    private final AdminAccountRepository accountRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminMfaSecretCipher mfaSecretCipher;

    @Autowired
    public AdminBootstrapService(AdminAccountRepository accountRepository,
                                 AdminAuditLogRepository auditLogRepository,
                                 PasswordEncoder passwordEncoder,
                                 AdminMfaSecretCipher mfaSecretCipher) {
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaSecretCipher = mfaSecretCipher;
    }

    AdminBootstrapService(AdminAccountRepository accountRepository,
                          AdminAuditLogRepository auditLogRepository,
                          PasswordEncoder passwordEncoder) {
        this(accountRepository, auditLogRepository, passwordEncoder, null);
    }

    @Transactional
    public AdminBootstrapResult provision(String email, String name, char[] rawPassword) {
        return provision(email, name, rawPassword, null);
    }

    @Transactional
    public AdminBootstrapResult provision(String email, String name, char[] rawPassword,
                                          String mfaSecret) {
        String normalizedEmail = normalizeEmail(email);
        var existing = accountRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            if (existing.get().getRole() == AdminRole.SUPER_ADMIN) {
                return AdminBootstrapResult.ALREADY_EXISTS;
            }
            throw new IllegalStateException("bootstrap email is already assigned to a non-super admin");
        }
        if (accountRepository.count() != 0) {
            throw new IllegalStateException("initial super admin can only be created in an empty admin store");
        }

        validatePassword(rawPassword);
        String passwordHash = passwordEncoder.encode(CharBuffer.wrap(rawPassword));
        AdminAccount account = AdminAccount.active(
                normalizedEmail, passwordHash, name, AdminRole.SUPER_ADMIN);
        if (mfaSecret != null && !mfaSecret.isBlank()) {
            if (mfaSecretCipher == null) {
                throw new IllegalStateException("admin MFA cipher is unavailable");
            }
            String normalizedMfaSecret = mfaSecret.replace(" ", "").toUpperCase(Locale.ROOT);
            AdminTotpVerifier.decodeBase32(normalizedMfaSecret);
            account.configureMfa(mfaSecretCipher.encrypt(normalizedMfaSecret), "admin-mfa-v1");
        }
        account = accountRepository.saveAndFlush(account);
        auditLogRepository.save(AdminAuditLog.record(
                account.getId(), "ADMIN_BOOTSTRAP", "ADMIN_ACCOUNT",
                account.getId().toString(), "initial super admin provisioning", null,
                "{\"role\":\"SUPER_ADMIN\",\"status\":\"ACTIVE\"}",
                AdminAuditResult.SUCCESS, "bootstrap-" + UUID.randomUUID(), null, null));
        return AdminBootstrapResult.CREATED;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("admin bootstrap email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(char[] rawPassword) {
        if (rawPassword == null
                || rawPassword.length < MINIMUM_PASSWORD_LENGTH
                || rawPassword.length > MAXIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "admin bootstrap password must contain 16 to 128 characters");
        }
        for (char character : rawPassword) {
            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException(
                        "admin bootstrap password must not contain control characters");
            }
        }
    }
}
