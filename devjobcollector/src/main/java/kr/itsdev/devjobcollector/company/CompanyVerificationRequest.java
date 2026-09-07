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
import java.time.LocalDateTime;
import java.util.Objects;
import kr.itsdev.devjobcollector.security.account.UserAccount;

@Entity
@Table(name = "company_verification_requests")
public class CompanyVerificationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    private UserAccount requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private CompanyVerificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyVerificationStatus status;

    @Column(name = "evidence_object_key", nullable = false, length = 500, updatable = false)
    private String evidenceObjectKey;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserAccount reviewedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected CompanyVerificationRequest() {
    }

    private CompanyVerificationRequest(Company company, UserAccount requestedBy,
                                       CompanyVerificationMethod method, String evidenceObjectKey,
                                       LocalDateTime requestedAt) {
        this.company = Objects.requireNonNull(company, "company is required");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy is required");
        this.method = Objects.requireNonNull(method, "method is required");
        this.evidenceObjectKey = requireEvidenceObjectKey(evidenceObjectKey);
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt is required");
        this.status = CompanyVerificationStatus.PENDING;
    }

    public static CompanyVerificationRequest pending(Company company, UserAccount requestedBy,
                                                     CompanyVerificationMethod method,
                                                     String evidenceObjectKey,
                                                     LocalDateTime requestedAt) {
        return new CompanyVerificationRequest(company, requestedBy, method, evidenceObjectKey, requestedAt);
    }

    public void approve(UserAccount reviewer, LocalDateTime reviewedAt) {
        requirePending();
        this.status = CompanyVerificationStatus.APPROVED;
        this.reviewedBy = Objects.requireNonNull(reviewer, "reviewer is required");
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt is required");
        this.rejectionReason = null;
    }

    public void reject(UserAccount reviewer, String rejectionReason, LocalDateTime reviewedAt) {
        requirePending();
        this.status = CompanyVerificationStatus.REJECTED;
        this.reviewedBy = Objects.requireNonNull(reviewer, "reviewer is required");
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt is required");
        this.rejectionReason = requireText(rejectionReason, "rejectionReason", 500);
    }

    private void requirePending() {
        if (status != CompanyVerificationStatus.PENDING) {
            throw CompanyVerificationException.alreadyReviewed();
        }
    }

    private static String requireEvidenceObjectKey(String value) {
        String key = requireText(value, "evidenceObjectKey", 500);
        if (key.startsWith("/") || key.contains("..") || key.contains("://")) {
            throw new IllegalArgumentException("evidenceObjectKey must be an opaque storage key");
        }
        return key;
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
    public Company getCompany() { return company; }
    public UserAccount getRequestedBy() { return requestedBy; }
    public CompanyVerificationMethod getMethod() { return method; }
    public CompanyVerificationStatus getStatus() { return status; }
    public String getEvidenceObjectKey() { return evidenceObjectKey; }
    public String getRejectionReason() { return rejectionReason; }
    public UserAccount getReviewedBy() { return reviewedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
