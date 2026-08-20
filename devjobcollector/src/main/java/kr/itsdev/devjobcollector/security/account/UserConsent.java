package kr.itsdev.devjobcollector.security.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_consents")
public class UserConsent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 50)
    private ConsentType consentType;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentAction action;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected UserConsent() {
    }

    private UserConsent(UserAccount user, ConsentType consentType, String policyVersion,
                        ConsentAction action, LocalDateTime occurredAt) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.consentType = Objects.requireNonNull(consentType, "consentType is required");
        this.policyVersion = requirePolicyVersion(policyVersion);
        this.action = Objects.requireNonNull(action, "action is required");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    public static UserConsent accepted(UserAccount user, ConsentType consentType,
                                       String policyVersion, LocalDateTime occurredAt) {
        return new UserConsent(user, consentType, policyVersion, ConsentAction.ACCEPTED, occurredAt);
    }

    public static UserConsent revoked(UserAccount user, ConsentType consentType,
                                      String policyVersion, LocalDateTime occurredAt) {
        return new UserConsent(user, consentType, policyVersion, ConsentAction.REVOKED, occurredAt);
    }

    private static String requirePolicyVersion(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("policyVersion is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 50) {
            throw new IllegalArgumentException("policyVersion exceeds 50 characters");
        }
        return trimmed;
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public ConsentType getConsentType() { return consentType; }
    public String getPolicyVersion() { return policyVersion; }
    public ConsentAction getAction() { return action; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
