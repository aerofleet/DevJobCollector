package kr.itsdev.devjobcollector.admin.companies;

import java.net.URI;
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
public class AdminPublicSeedReviewService {
    private final JdbcTemplate jdbc;
    private final AdminAuditLogRepository audit;
    public AdminPublicSeedReviewService(JdbcTemplate jdbc, AdminAuditLogRepository audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void setHomepage(long id, Long expectedVersion, String url, AdminPrincipal actor,
                            String requestId, String ip, String userAgent) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        String normalized = validUrl(url);
        if (expectedVersion == null || expectedVersion < 0 || expectedVersion == Long.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SEED_VERSION");
        int changed = jdbc.update("UPDATE public_company_seed SET homepage_candidate_url = ?, "
                        + "homepage_candidate_source = NULL, homepage_candidate_score = NULL, version = version + 1 "
                        + "WHERE id = ? AND version = ? AND status = 'DISCOVERED'",
                normalized, id, expectedVersion);
        if (changed == 0) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM public_company_seed WHERE id = ?", Integer.class, id);
            throw new ResponseStatusException(count != null && count > 0 ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND,
                    count != null && count > 0 ? "SEED_VERSION_CONFLICT" : "PUBLIC_SEED_NOT_FOUND");
        }
        audit.save(AdminAuditLog.record(actor.id(), "PUBLIC_SEED_HOMEPAGE_UPDATED", "PUBLIC_COMPANY_SEED",
                Long.toString(id), "홈페이지 후보 입력", "{\"version\":" + expectedVersion + "}",
                "{\"version\":" + (expectedVersion + 1) + "}", AdminAuditResult.SUCCESS, requestId,
                truncate(ip, 45), truncate(userAgent, 500)));
    }

    private static String validUrl(String value) {
        try {
            String url = value == null ? "" : value.trim();
            URI uri = URI.create(url);
            if (url.length() > 500 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !uri.getHost().contains(".") || uri.getHost().matches("[0-9.]+")
                    || uri.getRawFragment() != null) throw new IllegalArgumentException();
            return url;
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_HOMEPAGE_URL");
        }
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
}
