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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "user_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_identities_provider_subject",
                columnNames = {"provider", "provider_subject"}),
        @UniqueConstraint(name = "uk_user_identities_user_provider",
                columnNames = {"user_id", "provider"})
})
public class UserIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255, updatable = false)
    private String providerSubject;

    @Column(length = 255, updatable = false)
    private String issuer;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "provider_email_verified")
    private Boolean providerEmailVerified;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected UserIdentity() {
    }

    private UserIdentity(UserAccount user, AuthProvider provider, String providerSubject,
                         String issuer, String providerEmail, Boolean providerEmailVerified) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.provider = Objects.requireNonNull(provider, "provider is required");
        this.providerSubject = requireText(providerSubject, "providerSubject", 255);
        this.issuer = optionalText(issuer, "issuer", 255);
        this.providerEmail = optionalText(providerEmail, "providerEmail", 255);
        this.providerEmailVerified = providerEmailVerified;
    }

    public static UserIdentity local(UserAccount user) {
        String normalizedEmail = requireText(user.getEmail(), "user.email", 255)
                .toLowerCase(Locale.ROOT);
        return new UserIdentity(user, AuthProvider.LOCAL, normalizedEmail, null,
                normalizedEmail, user.getStatus() == UserAccountStatus.ACTIVE);
    }

    public static UserIdentity social(UserAccount user, AuthProvider provider, String providerSubject,
                                      String issuer, String providerEmail,
                                      Boolean providerEmailVerified) {
        if (provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("social provider must not be LOCAL");
        }
        return new UserIdentity(user, provider, providerSubject, issuer,
                providerEmail, providerEmailVerified);
    }

    public void updateProviderEmail(String providerEmail, Boolean providerEmailVerified) {
        this.providerEmail = optionalText(providerEmail, "providerEmail", 255);
        this.providerEmailVerified = providerEmailVerified;
    }

    public void recordSuccessfulLogin(LocalDateTime occurredAt) {
        this.lastLoginAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public String getIssuer() { return issuer; }
    public String getProviderEmail() { return providerEmail; }
    public Boolean getProviderEmailVerified() { return providerEmailVerified; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
