package kr.itsdev.devjobcollector.collection.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "job_source_occurrence", uniqueConstraints =
        @UniqueConstraint(name = "uk_occurrence_source", columnNames = {"provider", "target_id", "source_job_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobSourceOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private CompanySourceTarget target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SourceType provider;

    @Column(name = "source_job_id", nullable = false, length = 200)
    private String sourceJobId;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "apply_url", length = 1000)
    private String applyUrl;

    @Column(name = "source_title", nullable = false, length = 500)
    private String sourceTitle;

    @Column(name = "source_location", length = 500)
    private String sourceLocation;

    @Column(name = "source_status", nullable = false, length = 30)
    private String sourceStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at_source")
    private Instant updatedAtSource;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "missed_successful_runs", nullable = false)
    private int missedSuccessfulRuns;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public JobSourceOccurrence(CompanySourceTarget target, SourceType provider, String sourceJobId,
                               String sourceUrl, String applyUrl, String sourceTitle, String sourceLocation,
                               Instant publishedAt, Instant updatedAtSource, String contentHash, Instant seenAt) {
        this.target = target;
        this.provider = provider;
        this.sourceJobId = sourceJobId;
        this.sourceStatus = "ACTIVE";
        refresh(sourceUrl, applyUrl, sourceTitle, sourceLocation, publishedAt, updatedAtSource, contentHash, seenAt);
    }

    public void refresh(String sourceUrl, String applyUrl, String sourceTitle, String sourceLocation,
                        Instant publishedAt, Instant updatedAtSource, String contentHash, Instant seenAt) {
        this.sourceUrl = sourceUrl;
        this.applyUrl = applyUrl;
        this.sourceTitle = sourceTitle;
        this.sourceLocation = sourceLocation;
        this.publishedAt = publishedAt;
        this.updatedAtSource = updatedAtSource;
        this.contentHash = contentHash;
        this.lastSeenAt = seenAt;
        this.missedSuccessfulRuns = 0;
        this.sourceStatus = "ACTIVE";
    }

    public void linkJobPosting(Long jobPostingId) {
        this.jobPostingId = jobPostingId;
        this.primary = true;
    }

    public boolean recordMissing(int closeThreshold) {
        if (!"ACTIVE".equals(sourceStatus)) {
            return false;
        }
        this.missedSuccessfulRuns++;
        if (missedSuccessfulRuns >= closeThreshold) {
            this.sourceStatus = "CLOSED";
            return true;
        }
        return false;
    }
}
