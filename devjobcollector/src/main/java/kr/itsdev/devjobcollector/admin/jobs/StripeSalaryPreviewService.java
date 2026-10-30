package kr.itsdev.devjobcollector.admin.jobs;

import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.service.StripeSalaryEvidenceService;
import kr.itsdev.devjobcollector.service.StripeSalaryPageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StripeSalaryPreviewService {
    private final JobPostRepository jobs;
    private final StripeSalaryPageClient pages;
    private final StripeSalaryEvidenceService evidence;

    public record Preview(long jobId, long jobVersion, String sourceId, String sourceUrl,
                          String previousText, LocalDateTime previousCheckedAt, String status,
                          String sourceHash, List<String> paragraphs, LocalDateTime fetchedAt) { }

    public Preview preview(long jobId, AdminPrincipal actor) {
        if (actor == null || actor.role() == AdminRole.REVIEWER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        var job = jobs.findById(jobId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.getSourcePlatform() != SourcePlatform.GREENHOUSE || job.getOriginalSn() == null
                || !job.getOriginalSn().matches("stripe:[0-9]{1,20}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SOURCE");
        String sourceId = job.getOriginalSn().substring("stripe:".length());
        LocalDateTime fetchedAt = LocalDateTime.now();
        var html = pages.fetch(sourceId);
        var candidate = html.map(value -> evidence.preview(sourceId, value))
                .orElseGet(() -> new StripeSalaryEvidenceService.Preview("UNAVAILABLE", null, List.of()));
        return new Preview(jobId, job.getVersion(), sourceId, "https://stripe.com/jobs/search?gh_jid=" + sourceId,
                job.getSupplementalSalaryText(), job.getSupplementalSalaryCheckedAt(), candidate.status(),
                candidate.sourceHash(), candidate.paragraphs(), fetchedAt);
    }
}
