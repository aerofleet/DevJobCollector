package kr.itsdev.devjobcollector.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Generates a validated proposal only; it does not publish or alter an existing job. */
@Service
public class JobRoleAiProposalService {
    private final OpenAiEvidenceClient client;
    private final boolean enabled;

    public JobRoleAiProposalService(OpenAiEvidenceClient client, @Value("${ai.job-role.enabled:false}") boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    public Proposal propose(JobRoleAiInput.Prepared input) {
        if (!enabled || !client.available()) throw new IllegalStateException("JOB_ROLE_AI_DISABLED");
        if (input == null) throw new IllegalArgumentException("INVALID_ROLE_INPUT");
        var result = JobRoleAiRules.validate(client.suggestJobRole(input.title(), input.content()), input.evidenceSource());
        return new Proposal(input.inputHash(), result.mode(), result.role(), result.evidence());
    }

    public record Proposal(String inputHash, String mode, String role, String evidence) {}
}
