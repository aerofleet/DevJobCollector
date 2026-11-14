package kr.itsdev.devjobcollector.admin.companies;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.collection.discovery.AtsFingerprintService;
import kr.itsdev.devjobcollector.collection.discovery.RecruitBoardIdentity;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Explicit approval requires recent board verification and an administrator's ownership attestation. */
@Service
public class PublicSeedTargetApprovalService {
    private final JdbcTemplate jdbc;
    private final CompanySourceTargetRepository targets;
    private final AtsFingerprintService fingerprints;
    private final AdminAuditLogRepository audit;

    public PublicSeedTargetApprovalService(JdbcTemplate jdbc, CompanySourceTargetRepository targets,
            AtsFingerprintService fingerprints, AdminAuditLogRepository audit) {
        this.jdbc = jdbc; this.targets = targets; this.fingerprints = fingerprints; this.audit = audit;
    }

    @Transactional
    public long approve(long seedId, long candidateId, Long expectedVersion, String companyName,
                        boolean ownershipConfirmed, AdminPrincipal actor, String requestId, String ip, String agent) {
        if (actor == null) throw error(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (actor.role() == AdminRole.REVIEWER) throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        if (!ownershipConfirmed || expectedVersion == null || expectedVersion < 0 || companyName == null)
            throw error(HttpStatus.BAD_REQUEST, "OWNERSHIP_CONFIRMATION_REQUIRED");
        var rows = jdbc.query("SELECT s.company_name, s.homepage_candidate_url, s.version, s.status, a.status, "
                        + "a.provider, a.source_identifier, a.source_url, a.review_reference_url, a.board_verified_at, "
                        + "a.board_job_count, a.board_seed_version FROM public_company_seed s "
                        + "JOIN public_seed_ats_candidate a ON a.seed_id = s.id WHERE s.id = ? AND a.id = ? FOR UPDATE",
                (rs, i) -> new Candidate(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getObject(10, LocalDateTime.class), rs.getObject(11, Integer.class), rs.getObject(12, Long.class)),
                seedId, candidateId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "DISCOVERY_NOT_FOUND");
        var c = rows.getFirst();
        if (!"DISCOVERED".equals(c.seedStatus()) || !"BOARD_VERIFIED".equals(c.status()) || c.version() != expectedVersion
                || c.boardVersion() == null || c.boardVersion() != c.version() || c.boardJobs() == null || c.boardJobs() < 1
                || c.verifiedAt() == null || c.verifiedAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).minusHours(24))
                || c.companyName().length() > 150 || !c.companyName().equals(companyName.trim()))
            throw error(HttpStatus.CONFLICT, "DISCOVERY_APPROVAL_CONFLICT");
        try {
            URI homepage = URI.create(c.homepage());
            URI reference = URI.create(c.reference());
            if (!"https".equalsIgnoreCase(reference.getScheme()) || homepage.getHost() == null
                    || !homepage.getHost().equalsIgnoreCase(reference.getHost())) throw new IllegalArgumentException();
            if (SourceType.COMPANY_PAGE.name().equals(c.provider())
                    && !homepage.getHost().equalsIgnoreCase(URI.create(c.sourceUrl()).getHost()))
                throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw error(HttpStatus.CONFLICT, "OWNERSHIP_EVIDENCE_REQUIRED"); }
        var fp = (SourceType.COMPANY_PAGE.name().equals(c.provider())
                ? RecruitBoardIdentity.identify(c.sourceUrl()).map(id -> new AtsFingerprintService.Fingerprint(SourceType.COMPANY_PAGE, id))
                : fingerprints.identify(c.sourceUrl())).orElseThrow(() -> error(HttpStatus.CONFLICT, "INVALID_ATS_SOURCE"));
        if (!fp.provider().name().equals(c.provider()) || !fp.sourceIdentifier().equals(c.identifier()))
            throw error(HttpStatus.CONFLICT, "INVALID_ATS_SOURCE");
        if (targets.findByProviderAndSourceIdentifier(fp.provider(), fp.sourceIdentifier()).isPresent())
            throw error(HttpStatus.CONFLICT, "ATS_TARGET_EXISTS");
        var companies = jdbc.query("SELECT c.id, c.status FROM companies c JOIN public_company_seed s "
                        + "ON c.business_number_hash = s.business_number_hash WHERE s.id = ?",
                (rs, i) -> new MatchedCompany(rs.getLong(1), rs.getString(2)), seedId);
        if (!companies.isEmpty() && !"VERIFIED".equals(companies.getFirst().status()))
            throw error(HttpStatus.CONFLICT, "MATCHED_COMPANY_NOT_VERIFIED");
        Long companyId = companies.isEmpty() ? null : companies.getFirst().id();
        String url = fp.provider() == SourceType.GREETING
                ? "https://" + fp.sourceIdentifier() + ".career.greetinghr.com/ko/home" : c.sourceUrl();
        var target = new CompanySourceTarget(companyId, c.companyName(), fp.provider(), fp.sourceIdentifier(), url, CollectionTier.B);
        target.activate();
        try { targets.saveAndFlush(target); }
        catch (DataIntegrityViolationException duplicate) { throw error(HttpStatus.CONFLICT, "ATS_TARGET_EXISTS"); }
        jdbc.update("UPDATE public_seed_ats_candidate SET status = 'TARGET_CREATED', target_id = ? WHERE id = ? AND seed_id = ?",
                target.getId(), candidateId, seedId);
        audit.save(AdminAuditLog.record(actor.id(), "PUBLIC_SEED_ATS_TARGET_APPROVED", "PUBLIC_SEED_ATS_CANDIDATE",
                Long.toString(candidateId), "관리자 기업 소유권 확인", "{\"status\":\"BOARD_VERIFIED\"}",
                "{\"status\":\"TARGET_CREATED\",\"targetId\":" + target.getId() + "}", AdminAuditResult.SUCCESS,
                requestId, truncate(ip, 45), truncate(agent, 500)));
        return target.getId();
    }

    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    private static String truncate(String value, int limit) { return value == null || value.length() <= limit ? value : value.substring(0, limit); }
    private record MatchedCompany(Long id, String status) { }
    private record Candidate(String companyName, String homepage, long version, String seedStatus, String status,
                             String provider, String identifier, String sourceUrl, String reference,
                             LocalDateTime verifiedAt, Integer boardJobs, Long boardVersion) { }
}
