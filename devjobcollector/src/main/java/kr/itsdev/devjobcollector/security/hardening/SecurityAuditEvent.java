package kr.itsdev.devjobcollector.security.hardening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private SecurityAuditEventType eventType;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "subject_user_id", nullable = false, updatable = false)
    private Long subjectUserId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "previous_value", length = 50, updatable = false)
    private String previousValue;

    @Column(name = "new_value", length = 50, updatable = false)
    private String newValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected SecurityAuditEvent() {
    }

    private SecurityAuditEvent(SecurityAuditEventType eventType, Long actorUserId,
                               Long subjectUserId, Long companyId, String previousValue,
                               String newValue, LocalDateTime occurredAt) {
        this.eventType = Objects.requireNonNull(eventType, "eventType is required");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId is required");
        this.subjectUserId = Objects.requireNonNull(subjectUserId, "subjectUserId is required");
        this.companyId = Objects.requireNonNull(companyId, "companyId is required");
        this.previousValue = normalize(previousValue);
        this.newValue = normalize(newValue);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    public static SecurityAuditEvent occurred(SecurityAuditEventType eventType, Long actorUserId,
                                              Long subjectUserId, Long companyId,
                                              String previousValue, String newValue,
                                              LocalDateTime occurredAt) {
        return new SecurityAuditEvent(eventType, actorUserId, subjectUserId, companyId,
                previousValue, newValue, occurredAt);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("audit value exceeds 50 characters");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public SecurityAuditEventType getEventType() { return eventType; }
    public Long getActorUserId() { return actorUserId; }
    public Long getSubjectUserId() { return subjectUserId; }
    public Long getCompanyId() { return companyId; }
    public String getPreviousValue() { return previousValue; }
    public String getNewValue() { return newValue; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
