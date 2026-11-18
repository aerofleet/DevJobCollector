package kr.itsdev.devjobcollector.collection.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RawSnapshotRetention {
    private final JdbcTemplate jdbc;
    private volatile java.time.Instant nextEmptyCheck = java.time.Instant.MIN;

    // Small independent transactions limit pressure on the production database.
    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void purgeExpired() {
        if (java.time.Instant.now().isBefore(nextEmptyCheck)) return;
        int deleted = jdbc.update("DELETE FROM job_raw_snapshot WHERE fetched_at < UTC_TIMESTAMP() - INTERVAL 30 DAY ORDER BY id LIMIT 5000");
        if (deleted > 0) log.info("Expired raw snapshots deleted: {}", deleted);
        if (deleted < 5000) nextEmptyCheck = java.time.Instant.now().plus(java.time.Duration.ofDays(1));
    }
}
