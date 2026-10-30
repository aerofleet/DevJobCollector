package kr.itsdev.devjobcollector.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Explicit opt-in, bounded batches; dry run performs no database writes. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeSalaryBackfill {
    private final JdbcTemplate jdbc;
    private final StripeSalaryPageClient pages;
    private final StripeSalaryEvidenceService evidence;

    @Value("${djc.stripe-salary-backfill.enabled:false}")
    private boolean enabled;
    @Value("${djc.stripe-salary-backfill.write-enabled:false}")
    private boolean writeEnabled;

    record Row(long id, String originalSn, String previousText) { }
    public record PreviewItem(long jobId, String sourceId, String previousText, String status, String sourceHash, List<String> paragraphs) { }
    public record Result(int scanned, int valid, int saved, int unavailable, boolean dryRun, List<PreviewItem> previews) { }

    @Scheduled(initialDelayString = "${djc.stripe-salary-backfill.initial-delay-ms:120000}",
            fixedDelayString = "${djc.stripe-salary-backfill.delay-ms:600000}")
    public void scheduled() {
        if (enabled) {
            Result result = runBatch(writeEnabled);
            log.info("Stripe salary batch: scanned={}, valid={}, saved={}, unavailable={}, dryRun={}",
                    result.scanned(), result.valid(), result.saved(), result.unavailable(), result.dryRun());
        }
    }

    public Result runBatch(boolean write) {
        List<Row> rows = jdbc.query("SELECT id, original_sn, supplemental_salary_text FROM job_posts WHERE source_platform='GREENHOUSE' "
                        + "AND original_sn REGEXP '^stripe:[0-9]{1,20}$' AND is_active=1 "
                        + "AND (supplemental_salary_checked_at IS NULL OR supplemental_salary_checked_at < CURRENT_TIMESTAMP - INTERVAL 7 DAY) "
                        + "AND (supplemental_salary_attempted_at IS NULL OR supplemental_salary_attempted_at < CURRENT_TIMESTAMP - INTERVAL 1 DAY) "
                        + "ORDER BY id LIMIT 2", (rs, n) -> new Row(rs.getLong("id"), rs.getString("original_sn"), rs.getString("supplemental_salary_text")));
        int valid = 0, saved = 0, unavailable = 0;
        List<PreviewItem> previews = new ArrayList<>();
        for (Row row : rows) {
            if (write && jdbc.update("UPDATE job_posts SET supplemental_salary_attempted_at=CURRENT_TIMESTAMP WHERE id=? "
                    + "AND source_platform='GREENHOUSE' AND original_sn=? AND is_active=1 "
                    + "AND (supplemental_salary_checked_at IS NULL OR supplemental_salary_checked_at < CURRENT_TIMESTAMP - INTERVAL 7 DAY) "
                    + "AND (supplemental_salary_attempted_at IS NULL OR supplemental_salary_attempted_at < CURRENT_TIMESTAMP - INTERVAL 1 DAY)", row.id(), row.originalSn()) != 1) continue;
            String sourceId = row.originalSn().substring("stripe:".length());
            LocalDateTime fetchedAt = LocalDateTime.now();
            try {
                var html = pages.fetch(sourceId);
                if (html.isEmpty()) { unavailable++; previews.add(new PreviewItem(row.id(), sourceId, row.previousText(), "UNAVAILABLE", null, List.of())); continue; }
                var preview = evidence.preview(sourceId, html.get());
                previews.add(new PreviewItem(row.id(), sourceId, row.previousText(), preview.status(), preview.sourceHash(), preview.paragraphs()));
                if (!"PRESENT".equals(preview.status())) { unavailable++; continue; }
                valid++;
                if (write) {
                    evidence.save(row.id(), sourceId, html.get(), preview.sourceHash(), fetchedAt);
                    saved++;
                }
            } catch (RuntimeException ex) {
                unavailable++;
                log.warn("Stripe salary batch could not process a posting: {}", ex.getClass().getSimpleName());
            }
        }
        return new Result(rows.size(), valid, saved, unavailable, !write, List.copyOf(previews));
    }
}
