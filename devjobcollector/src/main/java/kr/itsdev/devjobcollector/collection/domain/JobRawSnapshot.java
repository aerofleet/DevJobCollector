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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "job_raw_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRawSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crawl_run_id", nullable = false)
    private CrawlRun crawlRun;

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

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_hash", nullable = false, length = 64)
    private String responseHash;

    @Lob
    @Column(name = "raw_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public JobRawSnapshot(CrawlRun crawlRun, CompanySourceTarget target, SourceType provider,
                          String sourceJobId, String sourceUrl, Integer httpStatus,
                          String responseHash, String rawPayload, Instant fetchedAt) {
        this.crawlRun = crawlRun;
        this.target = target;
        this.provider = provider;
        this.sourceJobId = sourceJobId;
        this.sourceUrl = sourceUrl;
        this.httpStatus = httpStatus;
        this.responseHash = responseHash;
        this.rawPayload = rawPayload;
        this.fetchedAt = fetchedAt;
    }
}
