package kr.itsdev.devjobcollector.collection.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.itsdev.devjobcollector.collection.adapter.JobSourceAdapter;
import kr.itsdev.devjobcollector.collection.config.AtsCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.*;
import kr.itsdev.devjobcollector.collection.repository.*;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CollectionSnapshotDedupTest {
    @Test void unchangedSnapshotIsSkippedButProjectionStillRuns() { exercise("same", false); }
    @Test void changedSnapshotIsStored() { exercise("different", true); }

    private void exercise(String oldHash, boolean expectedSave) {
        var targets = mock(CompanySourceTargetRepository.class);
        var runs = mock(CrawlRunRepository.class);
        var snapshots = mock(JobRawSnapshotRepository.class);
        var occurrences = mock(JobSourceOccurrenceRepository.class);
        var posts = mock(JobPostRepository.class);
        var projection = mock(JobPostProjectionService.class);
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        var target = new CompanySourceTarget(null, SourceType.COMPANY_PAGE, "board", "https://example.com", CollectionTier.B);
        target.activate(); ReflectionTestUtils.setField(target, "id", 7L);
        when(targets.findById(7L)).thenReturn(Optional.of(target));
        when(runs.save(any(CrawlRun.class))).thenAnswer(i -> {
            CrawlRun run = i.getArgument(0); ReflectionTestUtils.setField(run, "id", 9L);
            when(runs.findById(9L)).thenReturn(Optional.of(run)); return run;
        });
        var occurrence = new JobSourceOccurrence(target, SourceType.COMPANY_PAGE, "1", "https://example.com/1", null,
                "Developer", null, null, null, oldHash, Instant.now());
        when(occurrences.findByProviderAndTargetIdAndSourceJobId(SourceType.COMPANY_PAGE, 7L, "1"))
                .thenReturn(Optional.of(occurrence));
        var job = new JobRawDto(SourceType.COMPANY_PAGE, "1", "Developer", null, null, null,
                "https://example.com/1", null, "description", null, null, "same", "payload");
        var adapter = mock(JobSourceAdapter.class);
        when(adapter.sourceType()).thenReturn(SourceType.COMPANY_PAGE);
        when(adapter.fetchJobs(any(), any())).thenReturn(new CollectionResult(CollectionStatus.SUCCESS,
                List.of(job), 1, "test", false, "feed-hash"));
        var post = mock(JobPost.class); when(post.getId()).thenReturn(10L);
        when(projection.upsert(target, job)).thenReturn(post);
        var service = new CollectionOrchestrator(targets, runs, snapshots, occurrences, posts, projection,
                new AtsCollectionProperties(true, 2, List.of()), List.of(adapter), tx);
        service.collectTarget(7L);
        verify(snapshots, times(expectedSave ? 1 : 0)).save(any(JobRawSnapshot.class));
        verify(projection).upsert(target, job);
    }
}
