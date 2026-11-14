package kr.itsdev.devjobcollector.admin.companies;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.collection.adapter.JobSourceAdapter;
import kr.itsdev.devjobcollector.collection.discovery.AtsFingerprintService;
import kr.itsdev.devjobcollector.collection.discovery.RecruitBoardIdentity;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Checks a supported ATS board without persisting jobs or creating a collection target. */
@Service
public class PublicSeedBoardVerificationService {
    private final JdbcTemplate jdbc;
    private final AtsFingerprintService fingerprints;
    private final Map<SourceType, JobSourceAdapter> adapters = new EnumMap<>(SourceType.class);
    private final TransactionTemplate transactions;
    private final AdminAuditLogRepository audit;

    public PublicSeedBoardVerificationService(JdbcTemplate jdbc, AtsFingerprintService fingerprints,
            List<JobSourceAdapter> available, org.springframework.transaction.PlatformTransactionManager transactionManager,
            AdminAuditLogRepository audit) {
        this.jdbc = jdbc;
        this.fingerprints = fingerprints;
        available.forEach(adapter -> adapters.put(adapter.sourceType(), adapter));
        this.transactions = new TransactionTemplate(transactionManager);
        this.audit = audit;
    }

    public int verify(long seedId, long candidateId, Long expectedVersion, AdminPrincipal actor,
                      String requestId, String ip, String agent) {
        if (actor == null) throw error(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (seedId < 1 || candidateId < 1 || expectedVersion == null || expectedVersion < 0)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_DISCOVERY_INPUT");
        var rows = jdbc.query("SELECT s.company_name, s.version, s.status, a.status, a.provider, "
                        + "a.source_identifier, a.source_url, a.review_note, a.review_reference_url FROM public_company_seed s "
                        + "JOIN public_seed_ats_candidate a ON a.seed_id = s.id WHERE s.id = ? AND a.id = ?",
                (rs, i) -> new Board(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)), seedId, candidateId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "DISCOVERY_NOT_FOUND");
        var board = rows.getFirst();
        if (!"DISCOVERED".equals(board.seedStatus()) || !("VERIFYING".equals(board.status()) || "BOARD_VERIFIED".equals(board.status()))
                || board.version() != expectedVersion)
            throw error(HttpStatus.CONFLICT, "DISCOVERY_STATUS_CONFLICT");
        var fingerprint = (SourceType.COMPANY_PAGE.name().equals(board.provider())
                ? RecruitBoardIdentity.identify(board.sourceUrl()).map(id -> new AtsFingerprintService.Fingerprint(SourceType.COMPANY_PAGE, id))
                : fingerprints.identify(board.sourceUrl()))
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "INVALID_ATS_SOURCE"));
        if (!fingerprint.provider().name().equals(board.provider()) || !fingerprint.sourceIdentifier().equals(board.identifier()))
            throw error(HttpStatus.CONFLICT, "INVALID_ATS_SOURCE");
        SourceType provider = fingerprint.provider();
        if (provider == SourceType.GREETING && "backpac".equals(board.identifier()))
            throw error(HttpStatus.CONFLICT, "UNSUPPORTED_ATS_BOARD");
        JobSourceAdapter adapter = adapters.get(provider);
        if (adapter == null) throw error(HttpStatus.CONFLICT, "UNSUPPORTED_ATS_BOARD");
        String url = provider == SourceType.GREETING
                ? fingerprints.greetingBoardUrl(board.sourceUrl(), board.identifier()) : board.sourceUrl();
        var target = new CompanySourceTarget(null, board.companyName(), provider, board.identifier(), url, CollectionTier.B);
        var result = adapter.fetchJobs(target, CollectionContext.now());
        if (result.status() != CollectionStatus.SUCCESS
                || (provider != SourceType.COMPANY_PAGE && !result.closureEvaluationAllowed()) || result.jobs().isEmpty()
                || result.jobs().stream().anyMatch(job -> job.provider() != provider))
            throw error(HttpStatus.CONFLICT, "ATS_BOARD_NOT_VERIFIED");
        int count = result.jobs().size();
        String schema = result.schemaVersion();
        if (schema != null && schema.length() > 100) throw error(HttpStatus.CONFLICT, "INVALID_ATS_SCHEMA");
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("UPDATE public_seed_ats_candidate a JOIN public_company_seed s ON s.id = a.seed_id "
                            + "SET a.status = 'BOARD_VERIFIED', a.board_verified_at = UTC_TIMESTAMP(6), "
                            + "a.board_job_count = ?, a.board_schema_version = ?, a.board_seed_version = ? "
                            + "WHERE a.seed_id = ? AND a.id = ? AND a.status = ? AND s.status = 'DISCOVERED' AND s.version = ? "
                            + "AND BINARY a.review_note <=> BINARY ? AND BINARY a.review_reference_url <=> BINARY ?",
                    count, schema, expectedVersion, seedId, candidateId, board.status(), expectedVersion,
                    board.reviewNote(), board.reviewReference());
            if (changed != 1) throw error(HttpStatus.CONFLICT, "DISCOVERY_STATUS_CONFLICT");
            audit.save(AdminAuditLog.record(actor.id(), "PUBLIC_SEED_ATS_BOARD_VERIFIED", "PUBLIC_SEED_ATS_CANDIDATE",
                    Long.toString(candidateId), "ATS 공고 응답 확인; 기업 소유권 미검증", "{\"status\":\"" + board.status() + "\"}",
                    "{\"status\":\"BOARD_VERIFIED\",\"jobCount\":" + count + "}", AdminAuditResult.SUCCESS,
                    requestId, truncate(ip, 45), truncate(agent, 500)));
        });
        return count;
    }

    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    private static String truncate(String value, int limit) { return value == null || value.length() <= limit ? value : value.substring(0, limit); }
    private record Board(String companyName, long version, String seedStatus, String status,
                         String provider, String identifier, String sourceUrl, String reviewNote, String reviewReference) { }
}
