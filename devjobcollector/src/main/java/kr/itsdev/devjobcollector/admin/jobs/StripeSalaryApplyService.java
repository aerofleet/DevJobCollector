package kr.itsdev.devjobcollector.admin.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.service.StripeSalaryEvidenceService;
import kr.itsdev.devjobcollector.service.StripeSalaryPageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StripeSalaryApplyService {
    private final JobPostRepository jobs;
    private final StripeSalaryPageClient pages;
    private final StripeSalaryEvidenceService evidence;
    private final AdminAuditLogRepository audits;
    private final ObjectMapper mapper;

    @Transactional
    public Map<String, Object> apply(long id, long expectedVersion, String expectedHash, AdminPrincipal actor, String requestId) {
        if (actor == null || actor.role() == AdminRole.REVIEWER) throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        var initial = jobs.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (initial.getSourcePlatform() != SourcePlatform.GREENHOUSE || initial.getOriginalSn() == null
                || !initial.getOriginalSn().matches("stripe:[0-9]{1,20}")) throw error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SOURCE");
        if (initial.getVersion() != expectedVersion) throw error(HttpStatus.CONFLICT, "JOB_CHANGED");
        String sourceId = initial.getOriginalSn().substring("stripe:".length());
        LocalDateTime fetchedAt = LocalDateTime.now();
        String html = pages.fetch(sourceId).orElseThrow(() -> error(HttpStatus.SERVICE_UNAVAILABLE, "SOURCE_UNAVAILABLE"));
        var candidate = evidence.preview(sourceId, html);
        if (!"PRESENT".equals(candidate.status()) || expectedHash == null || !expectedHash.equals(candidate.sourceHash()))
            throw error(HttpStatus.CONFLICT, "SOURCE_CHANGED");
        var job = jobs.findByIdForModeration(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.getVersion() != expectedVersion || !job.getOriginalSn().equals("stripe:" + sourceId)) throw error(HttpStatus.CONFLICT, "JOB_CHANGED");
        var before = mapper.createObjectNode();
        before.put("text", job.getSupplementalSalaryText());
        before.put("sourceUrl", job.getSupplementalSalarySourceUrl());
        before.put("sourceHash", job.getSupplementalSalarySourceHash());
        before.put("checkedAt", job.getSupplementalSalaryCheckedAt() == null ? null : job.getSupplementalSalaryCheckedAt().toString());
        String url = "https://stripe.com/jobs/search?gh_jid=" + sourceId;
        job.recordSupplementalSalary(String.join("\n\n", candidate.paragraphs()), url, candidate.sourceHash(), fetchedAt);
        jobs.saveAndFlush(job);
        var after = mapper.createObjectNode();
        after.put("text", job.getSupplementalSalaryText()); after.put("sourceUrl", url);
        after.put("sourceHash", candidate.sourceHash()); after.put("checkedAt", fetchedAt.toString());
        audits.save(AdminAuditLog.record(actor.id(), "STRIPE_SALARY_APPLY", "JOB", Long.toString(id),
                "원문 급여 근거 반영", before.toString(), after.toString(), AdminAuditResult.SUCCESS, requestId, null, null));
        return Map.of("status", "APPLIED", "version", job.getVersion());
    }
    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
}
