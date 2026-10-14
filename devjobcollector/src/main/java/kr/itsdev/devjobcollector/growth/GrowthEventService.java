package kr.itsdev.devjobcollector.growth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GrowthEventService {
    private final JdbcTemplate jdbc;
    public GrowthEventService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(GrowthEventController.Event e) {
        jdbc.update("""
                INSERT INTO growth_events(event_id,visitor_id,session_id,kind,page,job_id,source,medium,campaign,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE event_id=event_id
                """, e.eventId().toString(), e.visitorId().toString(), e.sessionId().toString(),
                e.kind().name(), e.page(), e.jobId(), e.source(), e.medium(), e.campaign());
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timezone", "UTC");
        result.put("windowDays", 28);
        result.put("scope", "동의한 브라우저 기준 · 봇/테스트 트래픽이 포함될 수 있음 · 지원 클릭은 지원 완료가 아님");
        result.put("stages", jdbc.queryForList("""
                SELECT kind, COUNT(*) AS events, COUNT(DISTINCT visitor_id) AS visitors,
                    COUNT(DISTINCT session_id) AS sessions
                FROM growth_events WHERE created_at >= UTC_TIMESTAMP() - INTERVAL 28 DAY
                GROUP BY kind ORDER BY kind
                """));
        result.put("channels", jdbc.queryForList("""
                SELECT source,medium,campaign,COUNT(DISTINCT session_id) AS sessions,
                    COUNT(DISTINCT CASE WHEN kind IN ('BOOKMARK','APPLY_CLICK') THEN session_id END) AS activatedSessions
                FROM growth_events WHERE created_at >= UTC_TIMESTAMP() - INTERVAL 28 DAY
                GROUP BY source,medium,campaign ORDER BY sessions DESC LIMIT 50
                """));
        result.put("daily", jdbc.queryForList("""
                SELECT DATE(created_at) AS date, COUNT(DISTINCT visitor_id) AS visitors,
                    COUNT(DISTINCT CASE WHEN kind IN ('BOOKMARK','APPLY_CLICK') THEN visitor_id END) AS activatedVisitors
                FROM growth_events WHERE created_at >= UTC_TIMESTAMP() - INTERVAL 28 DAY
                GROUP BY DATE(created_at) ORDER BY date
                """));
        result.put("retention", jdbc.queryForMap("""
                WITH first_visits AS (
                    SELECT visitor_id, DATE(MIN(created_at)) AS first_day FROM growth_events
                    WHERE kind='PAGE_VIEW' AND created_at >= UTC_TIMESTAMP() - INTERVAL 90 DAY
                    GROUP BY visitor_id
                ), mature AS (
                    SELECT * FROM first_visits WHERE first_day >= UTC_DATE() - INTERVAL 35 DAY
                    AND first_day < UTC_DATE() - INTERVAL 7 DAY
                )
                SELECT COUNT(*) AS eligibleVisitors, COALESCE(SUM(EXISTS(
                    SELECT 1 FROM growth_events e WHERE e.visitor_id=m.visitor_id AND e.kind='PAGE_VIEW'
                    AND e.created_at >= m.first_day + INTERVAL 7 DAY
                    AND e.created_at < m.first_day + INTERVAL 8 DAY)),0) AS returnedVisitors
                FROM mature m
                """));
        return result;
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "UTC")
    public void purgeExpired() {
        // Bounded batches keep retention cleanup from holding a large transaction.
        for (int i = 0; i < 100; i++) {
            if (jdbc.update("DELETE FROM growth_events WHERE created_at < UTC_TIMESTAMP() - INTERVAL 90 DAY LIMIT 10000") < 10000) break;
        }
    }
}
