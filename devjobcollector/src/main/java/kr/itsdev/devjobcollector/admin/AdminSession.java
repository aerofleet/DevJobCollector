package kr.itsdev.devjobcollector.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_sessions")
public class AdminSession {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false)
    private AdminAccount admin;

    @Column(name = "refresh_hash", nullable = false, unique = true, length = 64,
            columnDefinition = "CHAR(64)")
    private String refreshHash;

    @Column(name = "csrf_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String csrfHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "rotated_from_id", length = 36, columnDefinition = "CHAR(36)")
    private String rotatedFromId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminSession() {
    }

    private AdminSession(AdminAccount admin, String refreshHash, String csrfHash,
                         LocalDateTime issuedAt, LocalDateTime expiresAt, String rotatedFromId) {
        this.id = UUID.randomUUID().toString();
        this.admin = Objects.requireNonNull(admin, "admin is required");
        this.refreshHash = requireSha256(refreshHash, "refreshHash");
        this.csrfHash = requireSha256(csrfHash, "csrfHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        this.rotatedFromId = rotatedFromId;
    }

    public static AdminSession issue(AdminAccount admin, String refreshHash, String csrfHash,
                                     LocalDateTime issuedAt, LocalDateTime expiresAt) {
        return new AdminSession(admin, refreshHash, csrfHash, issuedAt, expiresAt, null);
    }

    public static AdminSession rotate(AdminAccount admin, String refreshHash, String csrfHash,
                                      LocalDateTime issuedAt, LocalDateTime expiresAt,
                                      String rotatedFromId) {
        return new AdminSession(admin, refreshHash, csrfHash, issuedAt, expiresAt,
                requireUuid(rotatedFromId));
    }

    public void revoke(LocalDateTime occurredAt) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        }
    }

    public boolean isUsableAt(LocalDateTime occurredAt) {
        return revokedAt == null && occurredAt.isBefore(expiresAt);
    }

    private static String requireSha256(String value, String field) {
        if (value == null || !value.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 hex value");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String requireUuid(String value) {
        return UUID.fromString(value).toString();
    }

    public String getId() { return id; }
    public AdminAccount getAdmin() { return admin; }
    public String getRefreshHash() { return refreshHash; }
    public String getCsrfHash() { return csrfHash; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRotatedFromId() { return rotatedFromId; }
}
