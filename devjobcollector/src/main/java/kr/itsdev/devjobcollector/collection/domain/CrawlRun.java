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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "crawl_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private CompanySourceTarget target;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CollectionStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "previous_item_count")
    private Integer previousItemCount;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "closure_evaluation_allowed", nullable = false)
    private boolean closureEvaluationAllowed;

    private CrawlRun(CompanySourceTarget target, Integer previousItemCount, Instant startedAt) {
        this.target = target;
        this.previousItemCount = previousItemCount;
        this.startedAt = startedAt;
        this.status = CollectionStatus.FAILED;
        this.closureEvaluationAllowed = false;
    }

    public static CrawlRun start(CompanySourceTarget target, Integer previousItemCount, Instant startedAt) {
        return new CrawlRun(target, previousItemCount, startedAt);
    }

    public void complete(CollectionStatus status, Integer httpStatus, int itemCount,
                         String errorType, boolean requestedClosureEvaluation, Instant finishedAt) {
        this.status = status;
        this.httpStatus = httpStatus;
        this.itemCount = itemCount;
        this.errorType = errorType;
        this.finishedAt = finishedAt;
        this.closureEvaluationAllowed = requestedClosureEvaluation && isSafeItemCount(itemCount);
    }

    private boolean isSafeItemCount(int currentItemCount) {
        if (status != CollectionStatus.SUCCESS || previousItemCount == null || previousItemCount == 0) {
            return status == CollectionStatus.SUCCESS && currentItemCount > 0;
        }
        double remainingRatio = currentItemCount / (double) previousItemCount;
        return currentItemCount > 0 && remainingRatio >= 0.5;
    }
}
