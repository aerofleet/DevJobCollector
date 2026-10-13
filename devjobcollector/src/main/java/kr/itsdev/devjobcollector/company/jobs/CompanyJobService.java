package kr.itsdev.devjobcollector.company.jobs;

import java.time.LocalDate;
import java.util.*;
import kr.itsdev.devjobcollector.company.*;
import kr.itsdev.devjobcollector.domain.*;
import kr.itsdev.devjobcollector.repository.*;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional
public class CompanyJobService {
    private final CompanyJobRepository drafts;
    private final CompanyJobAuditRepository audits;
    private final CompanyRepository companies;
    private final CompanyMemberRepository memberships;
    private final CompanyAuthorizationService authorization;
    private final CurrentMemberService currentMember;
    private final JobPostRepository posts;
    private final TechStackRepository stacks;
    private final JdbcTemplate jdbc;

    public record View(Long id, Long companyId, String companyName, CompanyJobContent content,
            CompanyJob.State state, String liveStatus, Long publishedJobId, long version,
            String reviewReason, long views, long applyClicks) {}
    public record Duplicate(Long id, String title, String companyName, String originalUrl) {}
    public record Detail(View job, List<CompanyJobAudit> history, List<Duplicate> duplicates) {}
    public record History(Long id, Long companyJobId, String title, String companyName,
            Long actorId, String actorType, String action, String reason, java.time.LocalDateTime createdAt) {}

    @Transactional(readOnly=true)
    public Page<History> reviewHistory(int page, String action, Long jobId) {
        if (action != null && !Set.of("CREATED", "EDITED", "SUBMITTED", "APPROVE", "REQUEST_CHANGES",
                "SUSPEND", "FORCE_CLOSE", "DELETE", "CLOSED", "EXPIRED").contains(action))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACTION");
        var events = audits.history(action, jobId, paging(page));
        Map<Long, CompanyJob> jobs = new HashMap<>();
        drafts.findAllById(events.stream().map(CompanyJobAudit::getCompanyJobId).distinct().toList())
                .forEach(job -> jobs.put(job.getId(), job));
        return events.map(a -> {
            CompanyJob job = jobs.get(a.getCompanyJobId());
            return new History(a.getId(), a.getCompanyJobId(), job.getContent().getTitle(),
                    job.getCompany().getDisplayName(), a.getActorId(), a.getActorType(), a.getAction(), a.getReason(), a.getCreatedAt());
        });
    }

    @Transactional(readOnly=true)
    public Page<View> list(String subject, Long companyId, int page) {
        authorization.authorize(companyId, currentMember.requireCurrentMember(subject).getId(), CompanyPermission.VIEW_COMPANY);
        return drafts.findByCompany_IdOrderByIdDesc(companyId, paging(page)).map(this::view);
    }

    public View create(String subject, Long companyId, CompanyJobContent.Input content) {
        Long actor = authorizeWrite(subject, companyId, CompanyPermission.CREATE_JOB_POST);
        CompanyJob job = drafts.saveAndFlush(new CompanyJob(companies.getReferenceById(companyId), CompanyJobContent.from(content)));
        audit(job, actor, "MEMBER", "CREATED", null);
        return view(job);
    }

    public View edit(String subject, Long companyId, Long id, long version, CompanyJobContent.Input content) {
        Long actor = authorizeWrite(subject, companyId, CompanyPermission.EDIT_JOB_POST);
        CompanyJob job = locked(companyId, id, version);
        if (job.getState() == CompanyJob.State.IN_REVIEW || job.getState() == CompanyJob.State.SUSPENDED
                || job.getState() == CompanyJob.State.CLOSED || job.getState() == CompanyJob.State.DELETED) throw conflict();
        job.edit(CompanyJobContent.from(content));
        audit(job, actor, "MEMBER", "EDITED", null);
        drafts.flush(); return view(job);
    }

    public View submit(String subject, Long companyId, Long id, long version) {
        Long actor = authorizeWrite(subject, companyId, CompanyPermission.EDIT_JOB_POST);
        CompanyJob job = locked(companyId, id, version);
        if (job.getState() != CompanyJob.State.DRAFT && job.getState() != CompanyJob.State.CHANGES_REQUESTED) throw conflict();
        job.getContent().validateSubmission(LocalDate.now());
        job.transition(CompanyJob.State.IN_REVIEW, null);
        audit(job, actor, "MEMBER", "SUBMITTED", null);
        drafts.flush(); return view(job);
    }

    public View close(String subject, Long companyId, Long id, long version) {
        Long actor = authorizeWrite(subject, companyId, CompanyPermission.EDIT_JOB_POST);
        CompanyJob job = locked(companyId, id, version);
        if (job.getState() == CompanyJob.State.CLOSED || job.getState() == CompanyJob.State.SUSPENDED
                || job.getState() == CompanyJob.State.DELETED) throw conflict();
        closeLive(job, JobModerationStatus.CLOSED);
        job.transition(CompanyJob.State.CLOSED, "기업 조기 마감");
        audit(job, actor, "MEMBER", "CLOSED", "기업 조기 마감");
        drafts.flush(); return view(job);
    }

    @Transactional(readOnly=true)
    public Page<View> reviewList(int page, CompanyJob.State state) {
        return (state == null ? drafts.findAll(PageRequest.of(validPage(page), 20, Sort.by("id").descending()))
                : drafts.findByStateOrderByUpdatedAtAsc(state, paging(page))).map(this::view);
    }

    @Transactional(readOnly=true)
    public Detail reviewDetail(Long id) {
        CompanyJob job = drafts.findById(id).orElseThrow(CompanyJobService::notFound);
        var c = job.getContent();
        List<Duplicate> duplicates = c.getApplyUrl().isBlank() ? List.of() : posts
                .findTop10ByCompanyNameIgnoreCaseAndOriginalUrlAndSourcePlatformNot(
                        job.getCompany().getDisplayName(), c.getApplyUrl(), SourcePlatform.COMPANY_DIRECT)
                .stream().map(p -> new Duplicate(p.getId(), p.getTitle(), p.getCompanyName(), p.getOriginalUrl())).toList();
        return new Detail(view(job), audits.findTop50ByCompanyJobIdOrderByIdDesc(id), duplicates);
    }

    @Transactional(readOnly=true)
    public View reviewByPublishedId(Long publishedId) {
        return view(drafts.findByPublishedJob_Id(publishedId).orElseThrow(CompanyJobService::notFound));
    }

    public View review(Long id, long version, String action, String reason, AdminPrincipal actor) {
        if (actor == null || actor.role() == AdminRole.REVIEWER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REASON");
        Long companyId = drafts.companyId(id).orElseThrow(CompanyJobService::notFound);
        Company company = companies.findByIdForUpdate(companyId).orElseThrow(CompanyJobService::notFound);
        CompanyJob job = locked(companyId, id, version);
        if (job.getState() == CompanyJob.State.DELETED) throw conflict();
        switch (action) {
            case "APPROVE" -> {
                if (job.getState() != CompanyJob.State.IN_REVIEW) throw conflict();
                if (company.getStatus() != CompanyStatus.VERIFIED) throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPANY_NOT_VERIFIED");
                publish(job);
            }
            case "REQUEST_CHANGES" -> {
                if (job.getState() != CompanyJob.State.IN_REVIEW) throw conflict();
                job.transition(CompanyJob.State.CHANGES_REQUESTED, reason.trim());
            }
            case "SUSPEND" -> {
                if (job.getState() == CompanyJob.State.SUSPENDED) throw conflict();
                closeLive(job, JobModerationStatus.HIDDEN);
                job.transition(CompanyJob.State.SUSPENDED, reason.trim());
            }
            case "FORCE_CLOSE" -> {
                if (job.getState() == CompanyJob.State.CLOSED) throw conflict();
                closeLive(job, JobModerationStatus.CLOSED);
                job.transition(CompanyJob.State.CLOSED, reason.trim());
            }
            case "DELETE" -> {
                // Retain the public row, member references and immutable audit trail.
                closeLive(job, JobModerationStatus.HIDDEN);
                job.transition(CompanyJob.State.DELETED, reason.trim());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACTION");
        }
        audit(job, actor.id(), "ADMIN", action, reason.trim());
        drafts.flush(); return view(job);
    }

    private void publish(CompanyJob job) {
        CompanyJobContent c = job.getContent();
        c.validateSubmission(LocalDate.now());
        JobPost post = job.getPublishedJob();
        if (post == null) {
            post = JobPost.builder().sourcePlatform(SourcePlatform.COMPANY_DIRECT).originalSn("company-job-" + job.getId())
                    .companyName(job.getCompany().getDisplayName()).title(c.getTitle()).jobCategory(c.getJobCategory())
                    .experience(c.getExperience()).location(c.getLocation()).hireType(c.getHireType())
                    .startDate(LocalDate.now()).endDate(c.getEndDate()).originalUrl(c.getApplyUrl())
                    .applyQual(c.getDescription()).build();
        } else {
            post = posts.findByIdForModeration(post.getId()).orElseThrow(CompanyJobService::notFound);
            if (post.getModerationStatus() == JobModerationStatus.HIDDEN) throw conflict();
            post.refreshFromSource(job.getCompany().getDisplayName(), c.getTitle(), c.getJobCategory(), c.getExperience(),
                    c.getLocation(), c.getHireType(), post.getStartDate(), c.getEndDate(), c.getApplyUrl(), c.getDescription(), null);
            post.getPostTags().clear();
            posts.flush();
        }
        // MySQL upsert prevents competing approvals creating duplicate stack names.
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Arrays.stream(c.getTechStacks().split(",")).map(String::trim).forEach(names::add);
        for (String name : names) {
            jdbc.update("INSERT INTO tech_stacks (stack_name) VALUES (?) ON DUPLICATE KEY UPDATE stack_name=stack_name", name);
            post.addTechStack(stacks.findByStackName(name).orElseThrow());
        }
        post.changeModerationStatus(JobModerationStatus.ACTIVE);
        job.publish(posts.saveAndFlush(post));
    }

    public void expire(Long id) {
        Long companyId = drafts.companyId(id).orElse(null);
        if (companyId == null) return;
        companies.findByIdForUpdate(companyId).orElseThrow(CompanyJobService::notFound);
        CompanyJob job = drafts.lock(companyId, id).orElseThrow(CompanyJobService::notFound);
        if (job.getPublishedJob() == null) return;
        JobPost live = posts.findByIdForModeration(job.getPublishedJob().getId()).orElseThrow(CompanyJobService::notFound);
        if (live.getModerationStatus() != JobModerationStatus.ACTIVE || !live.getEndDate().isBefore(LocalDate.now())) return;
        live.deactivate(); live.changeModerationStatus(JobModerationStatus.CLOSED);
        // Preserve a newer draft or pending revision, but close the expired approved version.
        if (job.getState() == CompanyJob.State.PUBLISHED) job.transition(CompanyJob.State.CLOSED, "마감일 경과");
        audit(job, null, "SYSTEM", "EXPIRED", "마감일 경과");
    }

    private Long authorizeWrite(String subject, Long companyId, CompanyPermission permission) {
        Long actor = currentMember.requireCurrentMember(subject).getId();
        companies.findByIdForUpdate(companyId).orElseThrow(CompanyJobService::notFound);
        CompanyMember membership = memberships.findByCompanyAndUserForUpdate(companyId, actor)
                .orElseThrow(CompanyAuthorizationException::accessDenied);
        authorization.authorize(membership, permission);
        return actor;
    }
    private CompanyJob locked(Long companyId, Long id, long version) {
        CompanyJob job = drafts.lock(companyId, id).orElseThrow(CompanyJobService::notFound);
        if (job.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "JOB_VERSION_CONFLICT");
        return job;
    }
    private void closeLive(CompanyJob job, JobModerationStatus status) {
        if (job.getPublishedJob() != null) {
            JobPost post = posts.findByIdForModeration(job.getPublishedJob().getId()).orElseThrow(CompanyJobService::notFound);
            post.deactivate(); post.changeModerationStatus(status);
        }
    }
    private void audit(CompanyJob job, Long actor, String type, String action, String reason) {
        audits.save(new CompanyJobAudit(job.getId(), actor, type, action, reason));
    }
    private View view(CompanyJob job) {
        JobPost live = job.getPublishedJob();
        String status = live == null ? "UNPUBLISHED" : live.getModerationStatus() != JobModerationStatus.ACTIVE
                ? live.getModerationStatus().name() : !live.isActive() || live.getEndDate().isBefore(LocalDate.now()) ? "CLOSED" : "ACTIVE";
        Long liveId = live == null ? null : live.getId();
        long views = liveId == null ? 0 : countEvents(liveId, "VIEW");
        long clicks = liveId == null ? 0 : countEvents(liveId, "APPLY_CLICK");
        return new View(job.getId(), job.getCompany().getId(), job.getCompany().getDisplayName(), job.getContent(),
                job.getState(), status, liveId, job.getVersion(), job.getReviewReason(), views, clicks);
    }
    private long countEvents(Long id, String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM company_job_events WHERE job_id=? AND kind=?", Long.class, id, kind);
    }
    private static int validPage(int page) {
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        return page;
    }
    private static Pageable paging(int page) { return PageRequest.of(validPage(page), 20); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "JOB_STATE_CONFLICT"); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPANY_JOB_NOT_FOUND"); }
}
