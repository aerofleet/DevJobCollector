package kr.itsdev.devjobcollector.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostRetention {
    private final JdbcTemplate jdbc;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void purgeExpired() {
        // Preserve member activity and audit history; locked posts cannot gain new references mid-delete.
        List<Long> ids = jdbc.queryForList("""
            SELECT p.id FROM job_posts p
            WHERE p.is_active = false AND p.end_date < ? AND p.source_platform <> 'COMPANY_DIRECT'
              AND NOT EXISTS (SELECT 1 FROM applications a WHERE a.job_post_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM job_bookmarks b WHERE b.job_post_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM job_view_history v WHERE v.job_post_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM ai_recommendation_feedback f WHERE f.job_post_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM job_metadata_ai_review r WHERE r.job_post_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM job_moderation_history m WHERE m.job_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM company_jobs c WHERE c.published_job_id = p.id)
              AND NOT EXISTS (SELECT 1 FROM company_job_events e WHERE e.job_id = p.id)
            ORDER BY p.id LIMIT 500 FOR UPDATE
            """, Long.class, LocalDate.now().minusDays(15));
        if (ids.isEmpty()) return;
        String params = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.update("DELETE FROM job_files WHERE job_post_id IN (" + params + ")", ids.toArray());
        jdbc.update("DELETE FROM post_tags WHERE job_post_id IN (" + params + ")", ids.toArray());
        int deleted = jdbc.update("DELETE FROM job_posts WHERE id IN (" + params + ")", ids.toArray());
        log.info("Expired job posts deleted: {}", deleted);
    }
}
