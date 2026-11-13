package kr.itsdev.devjobcollector.collection.discovery;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/** Durable shared quota guard for the NAVER API HUB search API. */
@Service
public class NaverSearchApiQuotaService {
    public static final int MAX_DAILY_CALLS = 25_000;
    public static final int MAX_MONTHLY_CALLS = 775_000;
    private final JdbcTemplate jdbc;
    private final int dailyLimit;
    private final int monthlyLimit;

    public NaverSearchApiQuotaService(JdbcTemplate jdbc,
            @Value("${collection.homepage-search.daily-limit:25000}") int dailyLimit,
            @Value("${collection.homepage-search.monthly-limit:775000}") int monthlyLimit) {
        this.jdbc = jdbc;
        this.dailyLimit = Math.max(0, Math.min(dailyLimit, MAX_DAILY_CALLS));
        this.monthlyLimit = Math.max(0, Math.min(monthlyLimit, MAX_MONTHLY_CALLS));
    }

    @Transactional
    public boolean reserveCall() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (!reserve("DAILY", today, dailyLimit)) return false;
        if (!reserve("MONTHLY", today.withDayOfMonth(1), monthlyLimit)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
        return true;
    }

    private boolean reserve(String period, LocalDate start, int limit) {
        if (limit == 0) return false;
        jdbc.update("INSERT INTO naver_search_api_usage (period_type, period_start, request_count, updated_at) "
                        + "VALUES (?, ?, 0, UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE updated_at = updated_at",
                period, start);
        return jdbc.update("UPDATE naver_search_api_usage SET request_count = request_count + 1, "
                        + "updated_at = UTC_TIMESTAMP(6) WHERE period_type = ? AND period_start = ? AND request_count < ?",
                period, start, limit) == 1;
    }
}
