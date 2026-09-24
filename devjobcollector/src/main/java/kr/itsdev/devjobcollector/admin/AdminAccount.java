package kr.itsdev.devjobcollector.admin;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "admin_accounts")
public class AdminAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminAccountStatus status = AdminAccountStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_account_permissions", joinColumns = @JoinColumn(name = "admin_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    private Set<AdminPermission> permissions = new HashSet<>();

    @Column(name = "mfa_secret_ciphertext", columnDefinition = "VARBINARY(512)")
    private byte[] mfaSecretCiphertext;

    @Column(name = "mfa_key_id", length = 100)
    private String mfaKeyId;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AdminAccount() {
    }

    private AdminAccount(String email, String passwordHash, String name, AdminRole role) {
        this.email = normalizeEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash", 100);
        this.name = requireText(name, "name", 100);
        this.role = java.util.Objects.requireNonNull(role, "role is required");
    }

    public static AdminAccount active(String email, String passwordHash, String name, AdminRole role) {
        return new AdminAccount(email, passwordHash, name, role);
    }

    public void grant(AdminPermission permission) {
        permissions.add(java.util.Objects.requireNonNull(permission, "permission is required"));
    }

    public void configureMfa(byte[] ciphertext, String keyId) {
        if (ciphertext == null || ciphertext.length == 0 || ciphertext.length > 512) {
            throw new IllegalArgumentException("mfa ciphertext must contain 1 to 512 bytes");
        }
        this.mfaSecretCiphertext = ciphertext.clone();
        this.mfaKeyId = requireText(keyId, "mfaKeyId", 100);
        this.credentialVersion++;
    }

    public void recordSuccessfulLogin(LocalDateTime occurredAt) {
        this.lastLoginAt = java.util.Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.failedAttempts = 0;
        this.lockedUntil = null;
        if (this.status == AdminAccountStatus.LOCKED) {
            this.status = AdminAccountStatus.ACTIVE;
        }
    }

    public void recordFailedLogin(LocalDateTime occurredAt, int maximumAttempts,
                                  java.time.Duration lockDuration) {
        java.util.Objects.requireNonNull(occurredAt, "occurredAt is required");
        java.util.Objects.requireNonNull(lockDuration, "lockDuration is required");
        if (maximumAttempts < 1 || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lock policy must be positive");
        }
        this.failedAttempts++;
        if (this.failedAttempts >= maximumAttempts) {
            this.status = AdminAccountStatus.LOCKED;
            this.lockedUntil = occurredAt.plus(lockDuration);
        }
    }

    public boolean isLockedAt(LocalDateTime occurredAt) {
        return status == AdminAccountStatus.LOCKED
                && lockedUntil != null
                && occurredAt.isBefore(lockedUntil);
    }

    public void disable() {
        this.status = AdminAccountStatus.DISABLED;
        this.credentialVersion++;
    }

    private static String normalizeEmail(String value) {
        String normalized = requireText(value, "email", 255).toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new IllegalArgumentException("email must contain @");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public AdminRole getRole() { return role; }
    public AdminAccountStatus getStatus() { return status; }
    public Set<AdminPermission> getPermissions() { return Set.copyOf(permissions); }
    public byte[] getMfaSecretCiphertext() {
        return mfaSecretCiphertext == null ? null : mfaSecretCiphertext.clone();
    }
    public String getMfaKeyId() { return mfaKeyId; }
    public int getFailedAttempts() { return failedAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public long getCredentialVersion() { return credentialVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
