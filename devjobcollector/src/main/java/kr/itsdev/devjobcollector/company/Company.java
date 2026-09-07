package kr.itsdev.devjobcollector.company;

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
import kr.itsdev.devjobcollector.security.account.UserAccount;

@Entity
@Table(name = "companies", uniqueConstraints = @UniqueConstraint(
        name = "uk_companies_business_number_hash", columnNames = "business_number_hash"))
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "business_number_hash", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String businessNumberHash;

    @Column(name = "business_number_masked", nullable = false, length = 20)
    private String businessNumberMasked;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyStatus status = CompanyStatus.PENDING_VERIFICATION;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Company() {
    }

    private Company(String legalName, String displayName, String businessNumberHash,
                    String businessNumberMasked, String websiteUrl, UserAccount createdBy) {
        this.legalName = requireText(legalName, "legalName", 200);
        this.displayName = requireText(displayName, "displayName", 150);
        this.businessNumberHash = requireBusinessNumberHash(businessNumberHash);
        this.businessNumberMasked = requireText(businessNumberMasked, "businessNumberMasked", 20);
        this.websiteUrl = optionalText(websiteUrl, "websiteUrl", 500);
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy is required");
    }

    public static Company pendingVerification(String legalName, String displayName,
                                              String businessNumberHash, String businessNumberMasked,
                                              String websiteUrl, UserAccount createdBy) {
        return new Company(legalName, displayName, businessNumberHash,
                businessNumberMasked, websiteUrl, createdBy);
    }

    public void changeStatus(CompanyStatus status) {
        this.status = Objects.requireNonNull(status, "status is required");
    }

    private static String requireBusinessNumberHash(String value) {
        String normalized = requireText(value, "businessNumberHash", 64).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("businessNumberHash must be a SHA-256 hex value");
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

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    public Long getId() { return id; }
    public String getLegalName() { return legalName; }
    public String getDisplayName() { return displayName; }
    public String getBusinessNumberHash() { return businessNumberHash; }
    public String getBusinessNumberMasked() { return businessNumberMasked; }
    public String getWebsiteUrl() { return websiteUrl; }
    public CompanyStatus getStatus() { return status; }
    public UserAccount getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
