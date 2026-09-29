package kr.itsdev.devjobcollector.admin.jobs;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditResult;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminJobService {
    private final JobPostRepository jobs;
    private final JobModerationHistoryRepository history;
    private final AdminAuditLogRepository audit;

    public AdminJobService(JobPostRepository jobs, JobModerationHistoryRepository history,
                           AdminAuditLogRepository audit) {
        this.jobs = jobs;
        this.history = history;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<JobView> list(String keyword, JobModerationStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        String term = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (term != null && term.length() > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD");
        return jobs.searchForAdmin(term, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(JobView::from);
    }

    @Transactional(readOnly = true)
    public JobView detail(Long id) {
        return JobView.from(jobs.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND")));
    }

    @Transactional
    public JobView moderate(Long id, JobModerationStatus target, long expectedVersion,
                            String reason, AdminPrincipal actor, String requestId,
                            String ipAddress, String userAgent) {
        if (actor.role() == AdminRole.REVIEWER) throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        if (target == null) throw error(HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REASON");
        }
        JobPost job = jobs.findByIdForModeration(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.getVersion() != expectedVersion) throw error(HttpStatus.CONFLICT, "JOB_VERSION_CONFLICT");
        JobModerationStatus previous = job.getModerationStatus();
        boolean valid = switch (target) {
            case HIDDEN -> previous == JobModerationStatus.ACTIVE;
            case ACTIVE -> previous == JobModerationStatus.HIDDEN;
            case CLOSED -> previous == JobModerationStatus.ACTIVE || previous == JobModerationStatus.HIDDEN;
        };
        if (!valid) throw error(HttpStatus.CONFLICT, "JOB_STATUS_CONFLICT");

        job.changeModerationStatus(target);
        jobs.saveAndFlush(job);
        history.save(new JobModerationHistory(id, previous.name(), target.name(),
                reason.trim(), actor.id()));
        audit.save(AdminAuditLog.record(actor.id(), "JOB_STATUS_CHANGED", "JOB",
                id.toString(), reason.trim(), "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"" + target + "\"}", AdminAuditResult.SUCCESS,
                requestId, truncate(ipAddress, 45), truncate(userAgent, 500)));
        return JobView.from(job);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    public record JobView(Long id, String title, String companyName, String sourcePlatform,
                          String moderationStatus, boolean active, LocalDate startDate,
                          LocalDate endDate, long version, LocalDateTime createdAt,
                          String originalUrl) {
        static JobView from(JobPost job) {
            return new JobView(job.getId(), job.getTitle(), job.getCompanyName(),
                    job.getSourcePlatform().name(), job.getModerationStatus().name(),
                    job.isActive(), job.getStartDate(), job.getEndDate(), job.getVersion(),
                    job.getCreatedAt(), job.getOriginalUrl());
        }
    }
}
