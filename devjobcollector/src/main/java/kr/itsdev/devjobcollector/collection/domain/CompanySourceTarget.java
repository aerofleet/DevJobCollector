package kr.itsdev.devjobcollector.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "company_source_target", uniqueConstraints =
        @UniqueConstraint(name = "uk_target_provider_identifier", columnNames = {"provider", "source_identifier"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanySourceTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SourceType provider;

    @Column(name = "source_identifier", nullable = false, length = 150)
    private String sourceIdentifier;

    @Column(name = "careers_url", length = 1000)
    private String careersUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TargetStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_tier", nullable = false, length = 10)
    private CollectionTier collectionTier;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "schema_version", length = 50)
    private String schemaVersion;

    @Column(name = "next_collect_at")
    private Instant nextCollectAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CompanySourceTarget(Long companyId, SourceType provider, String sourceIdentifier,
                               String careersUrl, CollectionTier collectionTier) {
        this.companyId = companyId;
        this.provider = provider;
        this.sourceIdentifier = requireIdentifier(sourceIdentifier);
        this.careersUrl = careersUrl;
        this.collectionTier = collectionTier;
        this.status = TargetStatus.DISCOVERED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isCollectable() {
        return status == TargetStatus.ACTIVE || status == TargetStatus.VERIFYING;
    }

    public void activate() {
        status = TargetStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public void recordSuccess(Instant checkedAt, Integer httpStatus, String schemaVersion, Instant nextCollectAt) {
        this.lastSuccessAt = checkedAt;
        this.lastCheckedAt = checkedAt;
        this.lastHttpStatus = httpStatus;
        this.schemaVersion = schemaVersion;
        this.nextCollectAt = nextCollectAt;
        this.consecutiveFailures = 0;
        this.status = TargetStatus.ACTIVE;
        this.updatedAt = checkedAt;
    }

    public void recordFailure(Instant checkedAt, Integer httpStatus, int degradeThreshold, Instant nextCollectAt) {
        this.lastFailureAt = checkedAt;
        this.lastCheckedAt = checkedAt;
        this.lastHttpStatus = httpStatus;
        this.nextCollectAt = nextCollectAt;
        this.consecutiveFailures++;
        if (consecutiveFailures >= degradeThreshold) {
            this.status = TargetStatus.DEGRADED;
        }
        this.updatedAt = checkedAt;
    }

    private static String requireIdentifier(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sourceIdentifier is required");
        }
        return value.trim();
    }
}
