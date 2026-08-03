package kr.itsdev.devjobcollector.collection.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlRunTest {

    private final CompanySourceTarget target = new CompanySourceTarget(
            null, SourceType.GREENHOUSE, "gitlab", "https://example.com", CollectionTier.A);

    @Test
    void allowsClosureEvaluationAfterNormalSuccessfulChange() {
        CrawlRun run = CrawlRun.start(target, 100, Instant.now());

        run.complete(CollectionStatus.SUCCESS, 200, 95, null, true, Instant.now());

        assertThat(run.isClosureEvaluationAllowed()).isTrue();
    }

    @Test
    void blocksClosureEvaluationAfterMassiveDrop() {
        CrawlRun run = CrawlRun.start(target, 100, Instant.now());

        run.complete(CollectionStatus.SUCCESS, 200, 15, null, true, Instant.now());

        assertThat(run.isClosureEvaluationAllowed()).isFalse();
    }

    @Test
    void blocksClosureEvaluationForFailedRun() {
        CrawlRun run = CrawlRun.start(target, 100, Instant.now());

        run.complete(CollectionStatus.FAILED, 503, 0, "UPSTREAM_503", true, Instant.now());

        assertThat(run.isClosureEvaluationAllowed()).isFalse();
    }
}
