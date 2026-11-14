package kr.itsdev.devjobcollector.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.collection.adapter.JobSourceAdapter;
import kr.itsdev.devjobcollector.collection.config.AtsCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.repository.*;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CollectionOrchestratorExplicitClosureTest {
    @Test
    void closesExplicitlyMarkedJobWhenNoActiveJobsRemain() {
        var targets = mock(CompanySourceTargetRepository.class);
        var runs = mock(CrawlRunRepository.class);
        var snapshots = mock(JobRawSnapshotRepository.class);
        var occurrences = mock(JobSourceOccurrenceRepository.class);
        var posts = mock(JobPostRepository.class);
        var projection = mock(JobPostProjectionService.class);
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var target = new CompanySourceTarget(null, SourceType.COMPANY_PAGE, "board", "https://example.com", CollectionTier.B);
        target.activate();
        ReflectionTestUtils.setField(target, "id", 7L);
        when(targets.findById(7L)).thenReturn(Optional.of(target));
        when(runs.save(any(CrawlRun.class))).thenAnswer(invocation -> {
            CrawlRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 9L);
            when(runs.findById(9L)).thenReturn(Optional.of(run));
            return run;
        });
        var occurrence = new JobSourceOccurrence(target, SourceType.COMPANY_PAGE, "1", "https://example.com/1",
                null, "개발자", null, null, null, "hash", Instant.now());
        occurrence.linkJobPosting(10L);
        when(occurrences.findByProviderAndTargetIdAndSourceJobId(SourceType.COMPANY_PAGE, 7L, "1"))
                .thenReturn(Optional.of(occurrence));
        var post = mock(JobPost.class);
        when(posts.findById(10L)).thenReturn(Optional.of(post));
        var adapter = mock(JobSourceAdapter.class);
        when(adapter.sourceType()).thenReturn(SourceType.COMPANY_PAGE);
        when(adapter.fetchJobs(any(), any())).thenReturn(new CollectionResult(CollectionStatus.EMPTY_SUCCESS,
                List.of(), 0, "test", false, "hash", Set.of("1")));
        var service = new CollectionOrchestrator(targets, runs, snapshots, occurrences, posts, projection,
                new AtsCollectionProperties(true, 2, List.of()), List.of(adapter), tx);

        service.collectTarget(7L);

        assertThat(occurrence.getSourceStatus()).isEqualTo("CLOSED");
        verify(post).deactivate();
        verify(occurrences, never()).findByTargetIdAndSourceStatus(any(), any());
    }
}
