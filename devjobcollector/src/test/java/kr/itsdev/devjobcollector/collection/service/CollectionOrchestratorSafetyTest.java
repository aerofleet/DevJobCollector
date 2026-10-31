package kr.itsdev.devjobcollector.collection.service;

import kr.itsdev.devjobcollector.collection.adapter.JobSourceAdapter;
import kr.itsdev.devjobcollector.collection.config.AtsCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.*;
import kr.itsdev.devjobcollector.collection.repository.*;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollectionOrchestratorSafetyTest {
    @Test void work24SnapshotsNeverEnterPublicProjectionOrClosure() {
        var fixture = new Fixture(SourceType.WORK24);
        var job = new JobRawDto(SourceType.WORK24, "a", "Company", "Developer", null, null, null, null,
                "https://example.com/job", null, null, null, null, null, "hash", "{}");
        when(fixture.adapter.fetchJobs(any(), any())).thenReturn(new CollectionResult(CollectionStatus.SUCCESS,
                List.of(job), 1, "v1", true, "hash"));
        fixture.orchestrator.collectTarget(1L);
        verify(fixture.snapshots).save(any());
        verifyNoInteractions(fixture.projection, fixture.occurrences);
    }

    @Test void suspiciousCountDropCannotReconcileMissingJobs() {
        var fixture = new Fixture(SourceType.GREENHOUSE);
        var previous = CrawlRun.start(fixture.target, null, Instant.EPOCH);
        previous.complete(CollectionStatus.SUCCESS, 200, 100, null, false, Instant.now());
        when(fixture.runs.findTopByTargetIdAndStatusOrderByStartedAtDesc(1L, CollectionStatus.SUCCESS))
                .thenReturn(Optional.of(previous));
        when(fixture.adapter.fetchJobs(any(), any())).thenReturn(new CollectionResult(CollectionStatus.SUCCESS,
                List.of(), 10, "v1", true, "hash"));
        fixture.orchestrator.collectTarget(1L);
        verifyNoInteractions(fixture.occurrences);
    }

    static class Fixture {
        final CompanySourceTargetRepository targets = mock(CompanySourceTargetRepository.class);
        final CrawlRunRepository runs = mock(CrawlRunRepository.class);
        final JobRawSnapshotRepository snapshots = mock(JobRawSnapshotRepository.class);
        final JobSourceOccurrenceRepository occurrences = mock(JobSourceOccurrenceRepository.class);
        final JobPostProjectionService projection = mock(JobPostProjectionService.class);
        final JobSourceAdapter adapter = mock(JobSourceAdapter.class);
        final CompanySourceTarget target;
        final CollectionOrchestrator orchestrator;
        Fixture(SourceType type) {
            target = new CompanySourceTarget(null, "Target", type, "1332", null, CollectionTier.B);
            target.activate();
            when(targets.findById(1L)).thenReturn(Optional.of(target));
            when(adapter.sourceType()).thenReturn(type);
            when(runs.save(any())).thenAnswer(invocation -> {
                CrawlRun run = invocation.getArgument(0);
                ReflectionTestUtils.setField(run, "id", 2L);
                when(runs.findById(2L)).thenReturn(Optional.of(run));
                return run;
            });
            PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
            when(tx.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
            orchestrator = new CollectionOrchestrator(targets, runs, snapshots, occurrences,
                    mock(JobPostRepository.class), projection, new AtsCollectionProperties(true, 2, List.of()), List.of(adapter), tx);
        }
    }
}
