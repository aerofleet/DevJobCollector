package kr.itsdev.devjobcollector.collection.service;

import kr.itsdev.devjobcollector.collection.adapter.JobSourceAdapter;
import kr.itsdev.devjobcollector.collection.config.AtsCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.CrawlRun;
import kr.itsdev.devjobcollector.collection.domain.JobRawSnapshot;
import kr.itsdev.devjobcollector.collection.domain.JobSourceOccurrence;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import kr.itsdev.devjobcollector.collection.repository.CrawlRunRepository;
import kr.itsdev.devjobcollector.collection.repository.JobRawSnapshotRepository;
import kr.itsdev.devjobcollector.collection.repository.JobSourceOccurrenceRepository;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CollectionOrchestrator {

    private static final int DEGRADE_THRESHOLD = 3;
    private static final Duration SUCCESS_INTERVAL = Duration.ofHours(2);
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(30);

    private final CompanySourceTargetRepository targetRepository;
    private final CrawlRunRepository crawlRunRepository;
    private final JobRawSnapshotRepository snapshotRepository;
    private final JobSourceOccurrenceRepository occurrenceRepository;
    private final JobPostRepository jobPostRepository;
    private final JobPostProjectionService projectionService;
    private final AtsCollectionProperties properties;
    private final Map<SourceType, JobSourceAdapter> adapters;
    private final TransactionTemplate transactionTemplate;

    public CollectionOrchestrator(
            CompanySourceTargetRepository targetRepository,
            CrawlRunRepository crawlRunRepository,
            JobRawSnapshotRepository snapshotRepository,
            JobSourceOccurrenceRepository occurrenceRepository,
            JobPostRepository jobPostRepository,
            JobPostProjectionService projectionService,
            AtsCollectionProperties properties,
            List<JobSourceAdapter> adapters,
            PlatformTransactionManager transactionManager
    ) {
        this.targetRepository = targetRepository;
        this.crawlRunRepository = crawlRunRepository;
        this.snapshotRepository = snapshotRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.jobPostRepository = jobPostRepository;
        this.projectionService = projectionService;
        this.properties = properties;
        this.adapters = new EnumMap<>(SourceType.class);
        adapters.forEach(adapter -> this.adapters.put(adapter.sourceType(), adapter));
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CollectionResult collectTarget(Long targetId) {
        RunState state = transactionTemplate.execute(status -> startRun(targetId));
        if (state == null) {
            throw new IllegalStateException("Failed to start collection run: " + targetId);
        }

        JobSourceAdapter adapter = adapters.get(state.target().getProvider());
        if (adapter == null) {
            throw new IllegalStateException("No adapter registered for: " + state.target().getProvider());
        }

        CollectionResult result;
        try {
            result = adapter.fetchJobs(state.target(), new CollectionContext(state.startedAt()));
        } catch (RuntimeException e) {
            result = CollectionResult.failed(CollectionStatus.FAILED, null);
            CollectionResult failedResult = result;
            transactionTemplate.executeWithoutResult(status -> finishRun(state.runId(), targetId, failedResult));
            throw e;
        }
        CollectionResult completedResult = result;
        try {
            transactionTemplate.executeWithoutResult(
                    status -> finishRun(state.runId(), targetId, completedResult));
        } catch (RuntimeException e) {
            CollectionResult failedResult = CollectionResult.failed(CollectionStatus.FAILED, result.schemaVersion());
            transactionTemplate.executeWithoutResult(
                    status -> finishRun(state.runId(), targetId, failedResult));
            throw e;
        }
        return result;
    }

    private RunState startRun(Long targetId) {
        CompanySourceTarget target = targetRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Collection target not found: " + targetId));
        if (!target.isCollectable()) {
            throw new IllegalStateException("Collection target is not active: " + targetId);
        }
        Instant startedAt = Instant.now();
        Integer previousCount = crawlRunRepository
                .findTopByTargetIdAndStatusOrderByStartedAtDesc(targetId, CollectionStatus.SUCCESS)
                .map(CrawlRun::getItemCount)
                .orElse(null);
        CrawlRun run = crawlRunRepository.save(CrawlRun.start(target, previousCount, startedAt));
        return new RunState(run.getId(), target, startedAt);
    }

    private void finishRun(Long runId, Long targetId, CollectionResult result) {
        CompanySourceTarget target = targetRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Collection target not found: " + targetId));
        CrawlRun run = crawlRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Crawl run not found: " + runId));
        Instant finishedAt = Instant.now();
        Integer httpStatus = successfulResponse(result.status()) ? 200
                : result.status() == CollectionStatus.RATE_LIMITED ? 429 : null;
        run.complete(result.status(), httpStatus, result.receivedCount(),
                successfulResponse(result.status()) ? null : result.status().name(),
                result.closureEvaluationAllowed(), finishedAt);

        if (successfulResponse(result.status())) {
            persistSuccessfulResult(run, target, result, finishedAt);
            if (result.closureEvaluationAllowed()) {
                reconcileMissingOccurrences(target, result.jobs());
            }
            target.recordSuccess(finishedAt, httpStatus, result.schemaVersion(), finishedAt.plus(SUCCESS_INTERVAL));
        } else {
            target.recordFailure(finishedAt, httpStatus, DEGRADE_THRESHOLD, finishedAt.plus(FAILURE_BACKOFF));
        }
    }

    private void persistSuccessfulResult(CrawlRun run, CompanySourceTarget target,
                                         CollectionResult result, Instant fetchedAt) {
        for (JobRawDto job : result.jobs()) {
            snapshotRepository.save(new JobRawSnapshot(
                    run, target, job.provider(), job.sourceJobId(), job.sourceUrl(), 200,
                    result.responseHash(), job.rawPayload(), fetchedAt));
            JobPost jobPost = projectionService.upsert(target, job);
            upsertOccurrence(target, job, jobPost.getId(), fetchedAt);
        }
    }

    private void upsertOccurrence(CompanySourceTarget target, JobRawDto job, Long jobPostingId, Instant seenAt) {
        JobSourceOccurrence occurrence = occurrenceRepository
                .findByProviderAndTargetIdAndSourceJobId(job.provider(), target.getId(), job.sourceJobId())
                .orElseGet(() -> new JobSourceOccurrence(
                        target, job.provider(), job.sourceJobId(), job.sourceUrl(), job.applyUrl(),
                        job.title(), job.location(), job.publishedAt(), job.updatedAtSource(),
                        job.contentHash(), seenAt));
        occurrence.refresh(job.sourceUrl(), job.applyUrl(), job.title(), job.location(),
                job.publishedAt(), job.updatedAtSource(), job.contentHash(), seenAt);
        occurrence.linkJobPosting(jobPostingId);
        occurrenceRepository.save(occurrence);
    }

    private void reconcileMissingOccurrences(CompanySourceTarget target, List<JobRawDto> currentJobs) {
        Set<String> seenSourceJobIds = currentJobs.stream()
                .map(JobRawDto::sourceJobId)
                .collect(Collectors.toSet());
        List<JobSourceOccurrence> activeOccurrences =
                occurrenceRepository.findByTargetIdAndSourceStatus(target.getId(), "ACTIVE");

        for (JobSourceOccurrence occurrence : activeOccurrences) {
            if (seenSourceJobIds.contains(occurrence.getSourceJobId())) {
                continue;
            }
            boolean closed = occurrence.recordMissing(properties.closureMissThreshold());
            if (closed && occurrence.getJobPostingId() != null) {
                jobPostRepository.findById(occurrence.getJobPostingId()).ifPresent(JobPost::deactivate);
            }
        }
    }

    private static boolean successfulResponse(CollectionStatus status) {
        return status == CollectionStatus.SUCCESS
                || status == CollectionStatus.PARTIAL_SUCCESS
                || status == CollectionStatus.EMPTY_SUCCESS;
    }

    private record RunState(Long runId, CompanySourceTarget target, Instant startedAt) {
    }
}
