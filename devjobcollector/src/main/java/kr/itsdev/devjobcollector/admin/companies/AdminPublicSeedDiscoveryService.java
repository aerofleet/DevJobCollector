package kr.itsdev.devjobcollector.admin.companies;

import java.util.List;
import java.net.URI;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.collection.discovery.HomepageAtsLinkDiscovery;
import kr.itsdev.devjobcollector.collection.discovery.SafeHomepageFetcher;
import kr.itsdev.devjobcollector.collection.discovery.RecruitBoardIdentity;
import kr.itsdev.devjobcollector.collection.adapter.RecruitBoardParser;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Discovers ATS links from the safely fetched, robots-aware homepage pages. */
@Service
public class AdminPublicSeedDiscoveryService {
    private final JdbcTemplate jdbc;
    private final HomepageAtsLinkDiscovery links;
    private final SafeHomepageFetcher fetcher;
    private final CompanySourceTargetRepository targets;
    private final AdminAuditLogRepository audit;
    private final RecruitBoardParser recruitBoardParser;

    public AdminPublicSeedDiscoveryService(JdbcTemplate jdbc, HomepageAtsLinkDiscovery links,
                                           SafeHomepageFetcher fetcher,
                                           CompanySourceTargetRepository targets, AdminAuditLogRepository audit,
                                           RecruitBoardParser recruitBoardParser) {
        this.jdbc = jdbc;
        this.links = links;
        this.fetcher = fetcher;
        this.targets = targets;
        this.audit = audit;
        this.recruitBoardParser = recruitBoardParser;
    }

    @Transactional
    public int discover(long seedId, Long expectedVersion, AdminPrincipal actor,
                        String requestId, String ip, String agent) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (expectedVersion == null || expectedVersion < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DISCOVERY_INPUT");
        var rows = jdbc.query("SELECT homepage_candidate_url, status, version FROM public_company_seed WHERE id = ? FOR UPDATE",
                (rs, row) -> new Seed(rs.getString(1), rs.getString(2), rs.getLong(3)), seedId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PUBLIC_SEED_NOT_FOUND");
        var seed = rows.getFirst();
        if (!"DISCOVERED".equals(seed.status()) || seed.homepage() == null || seed.version() != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SEED_VERSION_CONFLICT");
        var pages = fetcher.discoverablePages(seed.homepage());
        if (pages.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "HOMEPAGE_UNAVAILABLE_OR_ROBOTS_BLOCKED");
        List<HomepageAtsLinkDiscovery.Candidate> found = pages.stream()
                .flatMap(page -> links.discover(page.url().toString(), page.html()).stream())
                .distinct()
                .filter(candidate -> candidate.sourceUrl().length() <= 1000)
                .limit(100)
                .filter(candidate -> targets.findByProviderAndSourceIdentifier(candidate.fingerprint().provider(),
                        candidate.fingerprint().sourceIdentifier()).isEmpty()).toList();
        for (var candidate : found) {
            jdbc.update("INSERT INTO public_seed_ats_candidate "
                            + "(seed_id, provider, source_identifier, source_url, status, first_seen_at, last_seen_at, "
                            + "first_homepage_candidate_url, first_seed_version) "
                            + "VALUES (?, ?, ?, ?, 'DISCOVERED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?) "
                            + "ON DUPLICATE KEY UPDATE last_seen_at = UTC_TIMESTAMP(6)",
                    seedId, candidate.fingerprint().provider().name(), candidate.fingerprint().sourceIdentifier(),
                    candidate.sourceUrl(), seed.homepage(), seed.version());
        }
        int boardCount = 0;
        for (var page : pages) {
            var identity = RecruitBoardIdentity.identify(page.url().toString());
            if (identity.isEmpty() || targets.findByProviderAndSourceIdentifier(SourceType.COMPANY_PAGE, identity.get()).isPresent()) continue;
            var parsed = recruitBoardParser.parse(page.html(), page.url(), null);
            if (!parsed.valid() || parsed.jobs().isEmpty()) continue;
            jdbc.update("INSERT INTO public_seed_ats_candidate "
                            + "(seed_id, provider, source_identifier, source_url, status, first_seen_at, last_seen_at, "
                            + "first_homepage_candidate_url, first_seed_version) "
                            + "VALUES (?, ?, ?, ?, 'DISCOVERED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?) "
                            + "ON DUPLICATE KEY UPDATE last_seen_at = UTC_TIMESTAMP(6)",
                    seedId, SourceType.COMPANY_PAGE.name(), identity.get(), page.url().toString(), seed.homepage(), seed.version());
            boardCount++;
        }
        int candidateCount = found.size() + boardCount;
        audit.save(AdminAuditLog.record(actor.id(), "PUBLIC_SEED_HOMEPAGE_DISCOVERY", "PUBLIC_COMPANY_SEED",
                Long.toString(seedId), "안전한 홈페이지 페이지 자동 조회 및 ATS 링크 분석", null,
                "{\"candidateCount\":" + candidateCount + "}", AdminAuditResult.SUCCESS, requestId,
                truncate(ip, 45), truncate(agent, 500)));
        return candidateCount;
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
    private record Seed(String homepage, String status, long version) { }

    @Transactional
    public void startReview(long seedId, long candidateId, Long expectedSeedVersion, String note, String referenceUrl,
                            AdminPrincipal actor, String requestId, String ip, String agent) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (seedId < 1 || candidateId < 1 || expectedSeedVersion == null || expectedSeedVersion < 0
                || note == null || note.isBlank() || note.trim().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_INPUT");
        String reference = validateReference(referenceUrl);
        var states = jdbc.query("SELECT a.status FROM public_seed_ats_candidate a JOIN public_company_seed s ON s.id = a.seed_id "
                        + "WHERE a.seed_id = ? AND a.id = ? AND s.status = 'DISCOVERED' AND s.version = ? FOR UPDATE",
                (rs, row) -> rs.getString(1), seedId, candidateId, expectedSeedVersion);
        if (states.isEmpty() || !List.of("DISCOVERED", "VERIFYING", "BOARD_VERIFIED").contains(states.getFirst()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DISCOVERY_STATUS_CONFLICT");
        String previous = states.getFirst();
        int changed = jdbc.update("UPDATE public_seed_ats_candidate a JOIN public_company_seed s ON s.id = a.seed_id "
                        + "SET a.status = 'VERIFYING', a.review_note = ?, a.review_reference_url = ?, "
                        + "a.board_verified_at = NULL, a.board_job_count = NULL, a.board_schema_version = NULL, a.board_seed_version = NULL "
                        + "WHERE a.seed_id = ? AND a.id = ? AND a.status = ? "
                        + "AND s.status = 'DISCOVERED' AND s.version = ?",
                note.trim(), reference, seedId, candidateId, previous, expectedSeedVersion);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "DISCOVERY_STATUS_CONFLICT");
        audit.save(AdminAuditLog.record(actor.id(), "PUBLIC_SEED_ATS_REVIEW_STARTED", "PUBLIC_SEED_ATS_CANDIDATE",
                Long.toString(candidateId), note.trim(), "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"VERIFYING\"}", AdminAuditResult.SUCCESS, requestId,
                truncate(ip, 45), truncate(agent, 500)));
    }

    private static String validateReference(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        try {
            URI uri = URI.create(url);
            if (url.length() > 1000 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
                throw new IllegalArgumentException();
            return url;
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_REFERENCE");
        }
    }

    @Transactional
    public void reject(long seedId, long candidateId, String reason, AdminPrincipal actor,
                       String requestId, String ip, String agent) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (seedId < 1 || candidateId < 1 || reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_REASON");
        var states = jdbc.query("SELECT status FROM public_seed_ats_candidate WHERE seed_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> rs.getString(1), seedId, candidateId);
        if (states.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DISCOVERY_NOT_FOUND");
        String previous = states.getFirst();
        int changed = jdbc.update("UPDATE public_seed_ats_candidate SET status = 'REJECTED' "
                + "WHERE seed_id = ? AND id = ? AND status IN ('DISCOVERED', 'VERIFYING', 'BOARD_VERIFIED')", seedId, candidateId);
        if (changed == 0) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM public_seed_ats_candidate WHERE seed_id = ? AND id = ?",
                    Integer.class, seedId, candidateId);
            throw new ResponseStatusException(count != null && count > 0 ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND,
                    count != null && count > 0 ? "DISCOVERY_STATUS_CONFLICT" : "DISCOVERY_NOT_FOUND");
        }
        audit.save(AdminAuditLog.record(actor.id(), "PUBLIC_SEED_ATS_REJECTED", "PUBLIC_SEED_ATS_CANDIDATE",
                Long.toString(candidateId), reason.trim(), "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"REJECTED\"}", AdminAuditResult.SUCCESS, requestId,
                truncate(ip, 45), truncate(agent, 500)));
    }

    @Transactional(readOnly = true)
    public List<CandidateView> list(long seedId, long afterId, int size) {
        if (seedId < 1 || afterId < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DISCOVERY_FILTER");
        return jdbc.query("SELECT a.id, a.provider, a.source_identifier, a.source_url, a.status, "
                        + "a.first_homepage_candidate_url, a.first_seed_version, s.version, a.review_note, a.review_reference_url, "
                        + "a.board_verified_at, a.board_job_count, a.board_seed_version, a.discovery_source "
                        + "FROM public_seed_ats_candidate a JOIN public_company_seed s ON s.id = a.seed_id "
                        + "WHERE a.seed_id = ? AND a.id > ? ORDER BY a.id LIMIT ?",
                (rs, row) -> new CandidateView(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, Long.class),
                        rs.getLong(8), rs.getString(9), rs.getString(10), rs.getObject(11, java.time.LocalDateTime.class),
                        rs.getObject(12, Integer.class), rs.getObject(13, Long.class), rs.getString(14)), seedId, afterId, size);
    }

    public record CandidateView(long id, String provider, String sourceIdentifier, String sourceUrl, String status,
                                String firstHomepageCandidateUrl, Long firstSeedVersion, long currentSeedVersion,
                                String reviewNote, String reviewReferenceUrl, java.time.LocalDateTime boardVerifiedAt,
                                Integer boardJobCount, Long boardSeedVersion, String discoverySource) { }
}
