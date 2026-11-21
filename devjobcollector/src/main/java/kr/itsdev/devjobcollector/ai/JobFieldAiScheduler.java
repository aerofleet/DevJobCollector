package kr.itsdev.devjobcollector.ai;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded candidate scan; generating proposals never approves or publishes them. */
@Component
public class JobFieldAiScheduler {
    private static final Logger log = LoggerFactory.getLogger(JobFieldAiScheduler.class);
    private final JdbcTemplate jdbc;
    private final JobFieldAiProcessor processor;
    private final boolean enabled;
    private final AtomicLong cursor = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();

    public JobFieldAiScheduler(JdbcTemplate jdbc, JobFieldAiProcessor processor,
            @Value("${ai.job-field.batch-enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.processor = processor; this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "${ai.job-field.initial-delay-ms:300000}", fixedDelayString = "${ai.job-field.delay-ms:900000}")
    public void collect() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            var rows = jdbc.queryForList("""
                    SELECT j.id FROM job_posts j
                    WHERE j.id > ? AND j.is_active = true AND j.moderation_status = 'ACTIVE' AND j.end_date >= CURRENT_DATE
                    AND NOT EXISTS (SELECT 1 FROM job_field_ai_proposals p WHERE p.job_post_id=j.id AND p.job_version=j.version AND p.policy_version=?)
                    ORDER BY j.id LIMIT 200
                    """, cursor.get(), JobFieldAiPolicy.VERSION);
            if (rows.isEmpty()) { cursor.set(0); return; }
            int generated = 0, failed = 0;
            for (var row : rows) {
                long id = ((Number) row.get("id")).longValue();
                cursor.set(id);
                try {
                    var status = processor.process(id);
                    if (status == JobFieldAiProcessor.Outcome.DAILY_LIMIT || status == JobFieldAiProcessor.Outcome.DISABLED) {
                        cursor.set(id - 1);
                        break;
                    }
                    if ((status == JobFieldAiProcessor.Outcome.STORED || status == JobFieldAiProcessor.Outcome.STORED_PARTIAL) && ++generated >= 20) break;
                } catch (RuntimeException failure) { failed++; }
            }
            log.info("AI job field batch: scanned={}, generated={}, failed={}", rows.size(), generated, failed);
        } finally { running.set(false); }
    }
}
