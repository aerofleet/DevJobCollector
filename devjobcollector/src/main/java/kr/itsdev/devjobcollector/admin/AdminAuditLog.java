package kr.itsdev.devjobcollector.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_admin_id", updatable = false)
    private Long actorAdminId;

    @Column(nullable = false, length = 100, updatable = false)
    private String action;

    @Column(name = "target_type", nullable = false, length = 50, updatable = false)
    private String targetType;

    @Column(name = "target_id", length = 100, updatable = false)
    private String targetId;

    @Column(length = 500, updatable = false)
    private String reason;

    @Column(name = "before_json", columnDefinition = "json", updatable = false)
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "json", updatable = false)
    private String afterJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AdminAuditResult result;

    @Column(name = "request_id", nullable = false, length = 100, updatable = false)
    private String requestId;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    protected AdminAuditLog() {
    }

    private AdminAuditLog(Long actorAdminId, String action, String targetType, String targetId,
                          String reason, String beforeJson, String afterJson,
                          AdminAuditResult result, String requestId,
                          String ipAddress, String userAgent) {
        this.actorAdminId = actorAdminId;
        this.action = requireText(action, "action", 100);
        this.targetType = requireText(targetType, "targetType", 50);
        this.targetId = optionalText(targetId, "targetId", 100);
        this.reason = optionalText(reason, "reason", 500);
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.result = java.util.Objects.requireNonNull(result, "result is required");
        this.requestId = requireText(requestId, "requestId", 100);
        this.ipAddress = optionalText(ipAddress, "ipAddress", 45);
        this.userAgent = optionalText(userAgent, "userAgent", 500);
    }

    public static AdminAuditLog record(Long actorAdminId, String action, String targetType,
                                       String targetId, String reason, String beforeJson,
                                       String afterJson, AdminAuditResult result, String requestId,
                                       String ipAddress, String userAgent) {
        return new AdminAuditLog(actorAdminId, action, targetType, targetId, reason,
                beforeJson, afterJson, result, requestId, ipAddress, userAgent);
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

    private static String optionalText(String value, String field, int maxLength) {
        return value == null || value.isBlank() ? null : requireText(value, field, maxLength);
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public Long getActorAdminId() { return actorAdminId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public AdminAuditResult getResult() { return result; }
    public String getRequestId() { return requestId; }
}
