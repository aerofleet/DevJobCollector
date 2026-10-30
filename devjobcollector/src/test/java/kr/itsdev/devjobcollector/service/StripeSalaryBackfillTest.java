package kr.itsdev.devjobcollector.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StripeSalaryBackfillTest {
    @Test void dryRunDoesNotWriteAndFailureDoesNotEraseEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StripeSalaryPageClient pages = mock(StripeSalaryPageClient.class);
        StripeSalaryEvidenceService evidence = mock(StripeSalaryEvidenceService.class);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(new StripeSalaryBackfill.Row(11051, "stripe:8330801", "old")));
        when(pages.fetch("8330801")).thenReturn(Optional.of("page"));
        when(evidence.preview("8330801", "page")).thenReturn(new StripeSalaryEvidenceService.Preview("PRESENT", "hash", List.of("salary")));
        var backfill = new StripeSalaryBackfill(jdbc, pages, evidence);
        var previewResult = backfill.runBatch(false);
        assertThat(previewResult.dryRun()).isTrue();
        assertThat(previewResult.valid()).isEqualTo(1);
        assertThat(previewResult.previews().getFirst()).isEqualTo(new StripeSalaryBackfill.PreviewItem(11051, "8330801", "old", "PRESENT", "hash", List.of("salary")));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(evidence, never()).save(anyLong(), anyString(), anyString(), anyString(), any());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(pages.fetch("8330801")).thenReturn(Optional.empty());
        var failure = backfill.runBatch(true);
        assertThat(failure.unavailable()).isEqualTo(1);
        assertThat(failure.saved()).isZero();
        verify(evidence, never()).save(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test void appliesOnlyAfterSuccessfullyClaimingTheRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StripeSalaryPageClient pages = mock(StripeSalaryPageClient.class);
        StripeSalaryEvidenceService evidence = mock(StripeSalaryEvidenceService.class);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(new StripeSalaryBackfill.Row(11051, "stripe:8330801", null)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(pages.fetch("8330801")).thenReturn(Optional.of("page"));
        when(evidence.preview("8330801", "page")).thenReturn(new StripeSalaryEvidenceService.Preview("PRESENT", "hash", List.of("salary")));
        var result = new StripeSalaryBackfill(jdbc, pages, evidence).runBatch(true);
        assertThat(result.saved()).isEqualTo(1);
        verify(evidence).save(eq(11051L), eq("8330801"), eq("page"), eq("hash"), any());
    }
}
