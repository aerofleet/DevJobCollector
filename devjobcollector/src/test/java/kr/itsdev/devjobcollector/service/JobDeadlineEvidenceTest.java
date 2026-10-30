package kr.itsdev.devjobcollector.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class JobDeadlineEvidenceTest {
    @Test void extractsEnglishAndRetainsDateOnlyEvidence() {
        var result = JobDeadlineEvidence.resolve(LocalDate.of(9999, 12, 31), "Applications open: 1st October 2026\nApplications close: 30th November 2026");
        assertThat(result.status()).isEqualTo("PRESENT");
        assertThat(result.localDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(result.evidence()).isEqualTo("Applications close: 30th November 2026");
    }

    @Test void resolvesDatesWithoutGuessingAndReportsConflicts() {
        assertThat(JobDeadlineEvidence.resolve(null, "접수 마감: 2028년 2월 29일").localDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(JobDeadlineEvidence.resolve(null, "Closing date: 2027-02-29").status()).isEqualTo("AMBIGUOUS");
        assertThat(JobDeadlineEvidence.resolve(null, "Applications open: 30th November 2026").status()).isEqualTo("UNAVAILABLE");
        var conflict = JobDeadlineEvidence.resolve(LocalDate.of(2026, 11, 29), "Applications close: 30th November 2026");
        assertThat(conflict.status()).isEqualTo("CONFLICT");
        assertThat(conflict.localDate()).isNull();
        assertThat(conflict.candidates()).hasSize(2);
    }
}
