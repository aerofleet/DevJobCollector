package kr.itsdev.devjobcollector.admin.companies;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDiscoveryService {
    private static final Set<String> STATUSES = Set.of("DISCOVERED", "VERIFYING", "VERIFIED", "APPROVED",
            "TARGET_CREATED", "REJECTED", "DEAD", "DUPLICATE", "BLOCKED");
    private final JdbcTemplate jdbc;
    private final AdminAuditLogRepository audit;

    public AdminDiscoveryService(JdbcTemplate jdbc, AdminAuditLogRepository audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void reject(long id, String reason, AdminPrincipal actor, String requestId, String ip, String agent) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_REASON");
        }
        int changed = jdbc.update("UPDATE company_discovery_candidate SET status = 'REJECTED' "
                + "WHERE id = ? AND status = 'DISCOVERED'", id);
        if (changed == 0) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM company_discovery_candidate WHERE id = ?",
                    Integer.class, id);
            throw new ResponseStatusException(count != null && count > 0 ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND,
                    count != null && count > 0 ? "DISCOVERY_STATUS_CONFLICT" : "DISCOVERY_NOT_FOUND");
        }
        audit.save(AdminAuditLog.record(actor.id(), "DISCOVERY_CANDIDATE_REJECTED", "DISCOVERY_CANDIDATE",
                Long.toString(id), reason.trim(), "{\"status\":\"DISCOVERED\"}",
                "{\"status\":\"REJECTED\"}", AdminAuditResult.SUCCESS, requestId,
                truncate(ip, 45), truncate(agent, 500)));
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    @Transactional(readOnly = true)
    public List<CandidateView> list(String status, long afterId, int size) {
        if (afterId < 0 || size < 1 || size > 100 || (status != null && !STATUSES.contains(status))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DISCOVERY_FILTER");
        }
        return jdbc.query("SELECT d.id, d.company_id, c.display_name, COALESCE(d.homepage_url, '') AS homepage_url, d.provider, "
                        + "d.source_identifier, d.source_url, d.status, d.first_seen_at, d.last_seen_at, d.discovery_source "
                        + "FROM company_discovery_candidate d JOIN companies c ON c.id = d.company_id "
                        + "WHERE d.id > ? AND (? IS NULL OR d.status = ?) ORDER BY d.id LIMIT ?",
                (rs, row) -> new CandidateView(rs.getLong("id"), rs.getLong("company_id"),
                        rs.getString("display_name"), rs.getString("homepage_url"), rs.getString("provider"),
                        rs.getString("source_identifier"), rs.getString("source_url"), rs.getString("status"),
                        rs.getObject("first_seen_at", LocalDateTime.class),
                        rs.getObject("last_seen_at", LocalDateTime.class), rs.getString("discovery_source")),
                afterId, status, status, size);
    }

    public record CandidateView(long id, long companyId, String companyName, String homepageUrl,
                                String provider, String sourceIdentifier, String sourceUrl, String status,
                                LocalDateTime firstSeenAt, LocalDateTime lastSeenAt, String discoverySource) { }
}
