package kr.itsdev.devjobcollector.collection.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Optional;
import kr.itsdev.devjobcollector.collection.discovery.RecruitBoardIdentity;
import kr.itsdev.devjobcollector.collection.discovery.SafeHomepageFetcher;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import org.junit.jupiter.api.Test;

class RecruitBoardCollectorTest {
    private static final String BOARD = "https://example.com/bbs/board.php?bo_table=recruit";
    private final SafeHomepageFetcher fetcher = mock(SafeHomepageFetcher.class);
    private final RecruitBoardCollector collector = new RecruitBoardCollector(fetcher, new RecruitBoardParser());

    @Test
    void traversesPagesWithoutInferringClosureFromMissingRows() {
        when(fetcher.fetchPage(BOARD)).thenReturn(page(BOARD, row(1) + pagination(2)));
        when(fetcher.fetchPage(BOARD + "&page=2")).thenReturn(page(BOARD + "&page=2", row(2)));
        var result = collector.collect(target());
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.jobs()).extracting(job -> job.sourceJobId()).containsExactly("1", "2");
        assertThat(result.closureEvaluationAllowed()).isFalse();
    }

    @Test
    void interruptedTraversalReturnsPartialWithoutClosingMissingJobs() {
        when(fetcher.fetchPage(BOARD)).thenReturn(page(BOARD, row(1) + pagination(2)));
        when(fetcher.fetchPage(BOARD + "&page=2")).thenReturn(Optional.empty());
        var result = collector.collect(target());
        assertThat(result.status()).isEqualTo(CollectionStatus.PARTIAL_SUCCESS);
        assertThat(result.jobs()).hasSize(1);
        assertThat(result.closureEvaluationAllowed()).isFalse();
    }

    @Test
    void offsitePaginationAndDuplicateIdsCannotCloseJobs() {
        when(fetcher.fetchPage(BOARD)).thenReturn(page(BOARD, row(1)
                + "<div class='pagination'><a href='https://evil.example/x'>2</a></div>"));
        assertThat(collector.collect(target()).closureEvaluationAllowed()).isFalse();
        when(fetcher.fetchPage(BOARD)).thenReturn(page(BOARD, row(1) + pagination(2)));
        when(fetcher.fetchPage(BOARD + "&page=2")).thenReturn(page(BOARD + "&page=2", row(1)));
        assertThat(collector.collect(target()).closureEvaluationAllowed()).isFalse();
    }

    @Test
    void mismatchedIdentityNeverFetches() {
        var wrong = new CompanySourceTarget(null, "회사", SourceType.COMPANY_PAGE, "recruit-board-wrong", BOARD, CollectionTier.B);
        assertThat(collector.collect(wrong).status()).isEqualTo(CollectionStatus.FAILED);
        org.mockito.Mockito.verify(fetcher, org.mockito.Mockito.never()).fetchPage(anyString());
    }

    @Test
    void reportsExplicitClosuresOnlyForCompleteTraversalIncludingZeroActive() {
        String closedRow = row(1).replace("recruit-now", "recruit-end").replace("접수중", "채용마감");
        when(fetcher.fetchPage(BOARD)).thenReturn(page(BOARD, closedRow));
        var complete = collector.collect(target());
        assertThat(complete.status()).isEqualTo(CollectionStatus.EMPTY_SUCCESS);
        assertThat(complete.explicitlyClosedIds()).containsExactly("1");
        assertThat(complete.closureEvaluationAllowed()).isFalse();

        when(fetcher.fetchPage(BOARD)).thenReturn(page(BOARD, closedRow + pagination(2)));
        when(fetcher.fetchPage(BOARD + "&page=2")).thenReturn(Optional.empty());
        var incomplete = collector.collect(target());
        assertThat(incomplete.explicitlyClosedIds()).isEmpty();
    }

    private static CompanySourceTarget target() {
        return new CompanySourceTarget(null, "회사", SourceType.COMPANY_PAGE,
                RecruitBoardIdentity.identify(BOARD).orElseThrow(), BOARD, CollectionTier.B);
    }
    private static Optional<SafeHomepageFetcher.Page> page(String url, String html) {
        return Optional.of(new SafeHomepageFetcher.Page(URI.create(url), "<table>" + html + "</table>"));
    }
    private static String row(int id) {
        return "<tr><td><a href='/bbs/board.php?bo_table=recruit&amp;wr_id=" + id
                + "'>개발자</a></td><td><span class='badge recruit-now'>접수중</span></td>"
                + "<td>2026.11.01~2026.12.01</td></tr>";
    }
    private static String pagination(int page) {
        return "</table><div class='pagination'><a href='/bbs/board.php?bo_table=recruit&amp;page="
                + page + "'>" + page + "</a></div><table>";
    }
}
