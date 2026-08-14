package kr.itsdev.devjobcollector.security.account;

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
@Table(name = "users")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String role = "USER";

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserAccountStatus status = UserAccountStatus.PENDING_EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected UserAccount() {
    }

    private UserAccount(String email, String name, String passwordHash, AuthProvider provider,
                        String providerUserId, UserAccountStatus status) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.status = status;
        if (status == UserAccountStatus.ACTIVE) {
            this.emailVerifiedAt = LocalDateTime.now();
        }
    }

    public static UserAccount pendingLocal(String email, String name, String passwordHash) {
        return new UserAccount(email, name, passwordHash, AuthProvider.LOCAL, null,
                UserAccountStatus.PENDING_EMAIL);
    }

    public static UserAccount activeSocial(String email, String name, AuthProvider provider, String providerUserId) {
        return new UserAccount(email, name, null, provider, providerUserId, UserAccountStatus.ACTIVE);
    }

    public void activateEmail() {
        this.status = UserAccountStatus.ACTIVE;
        this.emailVerifiedAt = LocalDateTime.now();
    }

    public void updateSocialProfile(String email, String name, AuthProvider provider, String providerUserId) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.status = UserAccountStatus.ACTIVE;
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public UserAccountStatus getStatus() { return status; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
}
