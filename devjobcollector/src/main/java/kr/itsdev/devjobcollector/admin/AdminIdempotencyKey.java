package kr.itsdev.devjobcollector.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "admin_idempotency_keys")
public class AdminIdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)", updatable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "json")
    private String responseBody;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    protected AdminIdempotencyKey() {
    }

    private AdminIdempotencyKey(Long adminId, String key, String requestHash,
                                String responseBody, LocalDateTime expiresAt) {
        this.adminId = Objects.requireNonNull(adminId, "adminId is required");
        this.key = requireText(key, "key", 100);
        this.requestHash = requireHash(requestHash);
        this.responseStatus = 200;
        this.responseBody = requireText(responseBody, "responseBody", 1000);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    }

    public static AdminIdempotencyKey completed(Long adminId, String key, String requestHash,
                                                 String responseBody, LocalDateTime expiresAt) {
        return new AdminIdempotencyKey(adminId, key, requestHash, responseBody, expiresAt);
    }

    public boolean matches(String candidateHash) {
        return requestHash.equals(candidateHash);
    }

    private static String requireHash(String value) {
        String hash = requireText(value, "requestHash", 64);
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("requestHash must be SHA-256 hex");
        return hash;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
