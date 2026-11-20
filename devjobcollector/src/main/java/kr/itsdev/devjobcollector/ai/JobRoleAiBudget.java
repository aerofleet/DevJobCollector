package kr.itsdev.devjobcollector.ai;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserves the standard-price maximum before generation; failed requests are not refunded. */
@Service
public class JobRoleAiBudget {
    private static final int MAX_REQUESTS_PER_DAY = 20;
    private static final long MAX_MICRO_USD_PER_DAY = 50_000;
    private final JdbcTemplate jdbc;

    public JobRoleAiBudget(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean alreadyClaimed(long jobId, String inputHash) {
        if (jobId <= 0 || inputHash == null || !inputHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("INVALID_ROLE_BUDGET_INPUT");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM job_role_ai_claims WHERE job_post_id=? AND input_hash=?",
                Integer.class, jobId, inputHash);
        return count != null && count > 0;
    }

    /** Allows a failed generation to retry later without refunding its reserved daily budget. */
    public void releaseFailedClaim(long jobId, String inputHash) {
        if (jobId <= 0 || inputHash == null || !inputHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("INVALID_ROLE_BUDGET_INPUT");
        jdbc.update("DELETE FROM job_role_ai_claims WHERE job_post_id=? AND input_hash=?", jobId, inputHash);
    }

    @Transactional
    public Reservation reserve(long jobId, String inputHash, int inputTokens) {
        if (jobId <= 0 || inputHash == null || !inputHash.matches("[0-9a-f]{64}") || inputTokens < 1 || inputTokens > 2000)
            throw new IllegalArgumentException("INVALID_ROLE_BUDGET_INPUT");
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        // Standard rates: $0.75 per million input and $4.50 per million output tokens.
        long reservedMicros = (inputTokens * 750_000L + 300L * 4_500_000L + 999_999L) / 1_000_000L;
        try {
            jdbc.update("INSERT INTO job_role_ai_claims (job_post_id,input_hash,usage_day,reserved_micro_usd,created_at) VALUES (?,?,?,?,NOW(6))",
                    jobId, inputHash, day, reservedMicros);
        } catch (DuplicateKeyException duplicate) {
            return Reservation.ALREADY_CLAIMED;
        }
        jdbc.update("INSERT INTO job_role_ai_daily_budget (usage_day,reserved_requests,reserved_micro_usd) VALUES (?,0,0) ON DUPLICATE KEY UPDATE usage_day=usage_day", day);
        int reserved = jdbc.update("""
                UPDATE job_role_ai_daily_budget
                SET reserved_requests=reserved_requests+1,reserved_micro_usd=reserved_micro_usd+?
                WHERE usage_day=? AND reserved_requests < ? AND reserved_micro_usd + ? <= ?
                """, reservedMicros, day, MAX_REQUESTS_PER_DAY, reservedMicros, MAX_MICRO_USD_PER_DAY);
        if (reserved != 1) {
            jdbc.update("DELETE FROM job_role_ai_claims WHERE job_post_id=? AND input_hash=?", jobId, inputHash);
            return Reservation.DAILY_LIMIT;
        }
        return Reservation.RESERVED;
    }

    public enum Reservation { RESERVED, ALREADY_CLAIMED, DAILY_LIMIT }
}
