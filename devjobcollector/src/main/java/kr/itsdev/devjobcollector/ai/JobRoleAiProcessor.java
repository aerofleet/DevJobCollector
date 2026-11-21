package kr.itsdev.devjobcollector.ai;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Processes one public job into a non-public proposal. No caller or scheduler is enabled here. */
@Service
public class JobRoleAiProcessor {
    private static final String CATALOG_VERSION = "djc-job-roles-v1";
    private final JobPostRepository jobs;
    private final OpenAiEvidenceClient client;
    private final JobRoleAiProposalService proposals;
    private final JobRoleAiProposalStore store;
    private final JobRoleAiBudget budget;
    private final JobRoleAiCatalog catalog;
    private final boolean enabled;
    private final Set<String> allowedSources;

    public JobRoleAiProcessor(JobPostRepository jobs, OpenAiEvidenceClient client, JobRoleAiProposalService proposals,
            JobRoleAiProposalStore store, JobRoleAiBudget budget, JobRoleAiCatalog catalog,
            @Value("${ai.job-role.enabled:false}") boolean enabled,
            @Value("${ai.job-role.allowed-sources:}") String allowedSources) {
        this.jobs = jobs; this.client = client; this.proposals = proposals; this.store = store; this.budget = budget; this.catalog = catalog;
        this.enabled = enabled;
        this.allowedSources = Arrays.stream(allowedSources.split(",")).map(String::trim)
                .filter(source -> !source.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public Outcome process(long id) {
        if (!enabled || !client.available()) return Outcome.DISABLED;
        JobPost job = jobs.findById(id).orElse(null);
        if (!eligible(job)) return Outcome.NOT_ELIGIBLE;
        Set<String> allowedRoles = catalog.allowedRoles();
        JobRoleAiInput.Prepared input;
        try { input = prepare(job, allowedRoles); }
        catch (IllegalArgumentException invalid) { return Outcome.INPUT_UNUSABLE; }
        if (budget.alreadyClaimed(id, input.inputHash())) return Outcome.ALREADY_CLAIMED;
        int tokens = client.countJobRoleInputTokens(input.title(), input.category(), input.content(), input.allowedRoles());
        if (tokens > 2000) {
            try { input = prepareCompact(job, allowedRoles); }
            catch (IllegalArgumentException invalid) { return Outcome.INPUT_UNUSABLE; }
            if (budget.alreadyClaimed(id, input.inputHash())) return Outcome.ALREADY_CLAIMED;
            tokens = client.countJobRoleInputTokens(input.title(), input.category(), input.content(), input.allowedRoles());
            if (tokens > 2000) return Outcome.INPUT_UNUSABLE;
        }
        JobPost latest = jobs.findById(id).orElse(null);
        if (!eligible(latest) || latest.getVersion() != job.getVersion()) return Outcome.JOB_CHANGED;
        JobRoleAiInput.Prepared latestInput;
        try { latestInput = JobRoleAiInput.prepareMatching(latest.getId(), latest.getSourcePlatform().name(), latest.getTitle(), latest.getJobCategory(),
                latest.getApplyQual(), CATALOG_VERSION, OpenAiEvidenceClient.CONTENT_MODEL, allowedRoles, input.inputHash()); }
        catch (IllegalArgumentException invalid) { return Outcome.JOB_CHANGED; }
        if (!input.inputHash().equals(latestInput.inputHash())) return Outcome.JOB_CHANGED;
        var reservation = budget.reserve(id, input.inputHash(), tokens);
        if (reservation == JobRoleAiBudget.Reservation.ALREADY_CLAIMED) return Outcome.ALREADY_CLAIMED;
        if (reservation == JobRoleAiBudget.Reservation.DAILY_LIMIT) return Outcome.DAILY_LIMIT;
        JobRoleAiProposalService.Proposal proposal;
        try {
            proposal = proposals.propose(input);
        } catch (RuntimeException failure) {
            try { budget.releaseFailedClaim(id, input.inputHash()); }
            catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
            throw failure;
        }
        return switch (store.save(id, job.getVersion(), CATALOG_VERSION, input, proposal)) {
            case STORED -> Outcome.STORED;
            case JOB_CHANGED -> Outcome.JOB_CHANGED;
            case ALREADY_EXISTS -> Outcome.ALREADY_CLAIMED;
        };
    }

    private boolean eligible(JobPost job) {
        return job != null && job.isActive() && job.getModerationStatus() == JobModerationStatus.ACTIVE
                && job.getEndDate() != null && !job.getEndDate().isBefore(LocalDate.now())
                && allowedSources.contains(sourceKey(job));
    }

    private static String sourceKey(JobPost job) {
        if (job.getSourcePlatform() == null || job.getOriginalSn() == null) return "";
        String sourceId = job.getOriginalSn();
        int delimiter = sourceId.indexOf(':');
        if (delimiter <= 0) return "";
        return job.getSourcePlatform().name() + ":" + sourceId.substring(0, delimiter);
    }

    private static JobRoleAiInput.Prepared prepare(JobPost job, Set<String> allowedRoles) {
        return JobRoleAiInput.prepare(job.getId(), job.getSourcePlatform().name(), job.getTitle(), job.getJobCategory(), job.getApplyQual(),
                CATALOG_VERSION, OpenAiEvidenceClient.CONTENT_MODEL, allowedRoles);
    }

    private static JobRoleAiInput.Prepared prepareCompact(JobPost job, Set<String> allowedRoles) {
        return JobRoleAiInput.prepareCompact(job.getId(), job.getSourcePlatform().name(), job.getTitle(), job.getJobCategory(), job.getApplyQual(),
                CATALOG_VERSION, OpenAiEvidenceClient.CONTENT_MODEL, allowedRoles);
    }

    public enum Outcome { DISABLED, NOT_ELIGIBLE, INPUT_UNUSABLE, JOB_CHANGED, ALREADY_CLAIMED, DAILY_LIMIT, STORED }
}
