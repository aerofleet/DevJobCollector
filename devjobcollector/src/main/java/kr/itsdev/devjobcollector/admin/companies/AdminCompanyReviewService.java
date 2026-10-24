package kr.itsdev.devjobcollector.admin.companies;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminIdempotencyKey;
import kr.itsdev.devjobcollector.admin.AdminIdempotencyKeyRepository;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.company.CompanyRepository;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequestRepository;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCompanyReviewService {
    private final CompanyRepository companies;
    private final CompanyVerificationRequestRepository requests;
    private final AdminCompanyService companyViews;
    private final AdminAuditLogRepository audit;
    private final AdminIdempotencyKeyRepository idempotencyKeys;
    private final CompanyEvidenceService evidenceService;

    public AdminCompanyReviewService(CompanyRepository companies,
                                     CompanyVerificationRequestRepository requests,
                                     AdminCompanyService companyViews,
                                     AdminAuditLogRepository audit,
                                     AdminIdempotencyKeyRepository idempotencyKeys,
                                     CompanyEvidenceService evidenceService) {
        this.companies = companies;
        this.requests = requests;
        this.companyViews = companyViews;
        this.audit = audit;
        this.idempotencyKeys = idempotencyKeys;
        this.evidenceService = evidenceService;
    }

    @Transactional
    public AdminCompanyService.CompanyDetail review(Long companyId, Long requestId,
                                                    boolean approve, String reason, String idempotencyKey,
                                                    AdminPrincipal actor, String auditRequestId,
                                                    String ipAddress, String userAgent) {
        if (actor == null) throw error(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED");
        if (!approve && (reason == null || reason.isBlank() || reason.length() > 500)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_REASON");
        }
        try {
            if (idempotencyKey == null || !UUID.fromString(idempotencyKey).toString().equals(idempotencyKey)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalidKey) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
        }
        var company = companies.findByIdForUpdate(companyId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND"));
        var request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "VERIFICATION_REQUEST_NOT_FOUND"));
        if (!request.getCompany().getId().equals(companyId)) {
            throw error(HttpStatus.NOT_FOUND, "VERIFICATION_REQUEST_NOT_FOUND");
        }
        String requestHash = requestHash(companyId, requestId, approve, reason);
        var previous = idempotencyKeys.findForUpdate(actor.id(), idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().matches(requestHash)) {
                throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
            }
            return companyViews.detail(companyId);
        }
        if (company.getStatus() != CompanyStatus.PENDING_VERIFICATION
                || request.getStatus() != CompanyVerificationStatus.PENDING) {
            throw error(HttpStatus.CONFLICT, "VERIFICATION_REQUEST_NOT_PENDING");
        }
        try {
            evidenceService.readForAdminCompany(companyId, requestId);
        } catch (ResponseStatusException missingEvidence) {
            throw error(HttpStatus.CONFLICT, "VERIFICATION_EVIDENCE_MISSING");
        }
        LocalDateTime now = LocalDateTime.now();
        if (approve) {
            request.approveByAdmin(actor.id(), now);
            company.changeStatus(CompanyStatus.VERIFIED);
        } else {
            request.rejectByAdmin(actor.id(), reason.trim(), now);
            company.changeStatus(CompanyStatus.REJECTED);
        }
        requests.saveAndFlush(request);
        companies.saveAndFlush(company);
        String next = approve ? "APPROVED" : "REJECTED";
        audit.save(AdminAuditLog.record(actor.id(),
                approve ? "COMPANY_VERIFICATION_APPROVED" : "COMPANY_VERIFICATION_REJECTED",
                "COMPANY_VERIFICATION_REQUEST", requestId.toString(),
                approve ? "사업자등록증 확인" : reason.trim(),
                "{\"status\":\"PENDING\"}", "{\"status\":\"" + next + "\"}",
                AdminAuditResult.SUCCESS, auditRequestId,
                truncate(ipAddress, 45), truncate(userAgent, 500)));
        idempotencyKeys.save(AdminIdempotencyKey.completed(actor.id(), idempotencyKey, requestHash,
                "{\"companyId\":" + companyId + ",\"requestId\":" + requestId
                        + ",\"status\":\"" + next + "\"}", now.plusHours(24)));
        return companyViews.detail(companyId);
    }

    private static String requestHash(Long companyId, Long requestId, boolean approve, String reason) {
        String normalized = companyId + ":" + requestId + ":" + (approve ? "APPROVE" : "REJECT")
                + ":" + (reason == null ? "" : reason.trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
