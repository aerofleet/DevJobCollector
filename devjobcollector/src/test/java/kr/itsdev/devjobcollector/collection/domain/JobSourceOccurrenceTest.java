package kr.itsdev.devjobcollector.collection.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JobSourceOccurrenceTest {

    @Test
    void closesOnlyAfterConfiguredSuccessfulRunMissThreshold() {
        CompanySourceTarget target = new CompanySourceTarget(
                null, "GitLab", SourceType.GREENHOUSE, "gitlab",
                "https://about.gitlab.com/jobs", CollectionTier.A);
        JobSourceOccurrence occurrence = new JobSourceOccurrence(
                target, SourceType.GREENHOUSE, "job-1",
                "https://example.com/job-1", "https://example.com/job-1/apply",
                "Backend Engineer", "Remote", null, null,
                "a".repeat(64), Instant.parse("2026-08-03T00:00:00Z"));

        assertThat(occurrence.recordMissing(2)).isFalse();
        assertThat(occurrence.getSourceStatus()).isEqualTo("ACTIVE");
        assertThat(occurrence.getMissedSuccessfulRuns()).isEqualTo(1);

        assertThat(occurrence.recordMissing(2)).isTrue();
        assertThat(occurrence.getSourceStatus()).isEqualTo("CLOSED");
        assertThat(occurrence.getMissedSuccessfulRuns()).isEqualTo(2);
    }

    @Test
    void refreshResetsMissingCountAndReopensOccurrence() {
        CompanySourceTarget target = new CompanySourceTarget(
                null, "Integrate", SourceType.LEVER, "integrate",
                "https://jobs.lever.co/integrate", CollectionTier.A);
        Instant firstSeen = Instant.parse("2026-08-03T00:00:00Z");
        JobSourceOccurrence occurrence = new JobSourceOccurrence(
                target, SourceType.LEVER, "job-2",
                "https://example.com/job-2", null, "Engineer", "Seattle",
                null, null, "b".repeat(64), firstSeen);
        occurrence.recordMissing(1);

        occurrence.refresh(
                "https://example.com/job-2", null, "Engineer II", "Seattle",
                null, null, "c".repeat(64), firstSeen.plusSeconds(60));

        assertThat(occurrence.getSourceStatus()).isEqualTo("ACTIVE");
        assertThat(occurrence.getMissedSuccessfulRuns()).isZero();
        assertThat(occurrence.getSourceTitle()).isEqualTo("Engineer II");
    }
}
