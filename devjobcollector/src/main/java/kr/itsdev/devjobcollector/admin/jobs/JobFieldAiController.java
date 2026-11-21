package kr.itsdev.devjobcollector.admin.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.ai.JobFieldAiPolicy;
import kr.itsdev.devjobcollector.ai.JobFieldAiProcessor;
import kr.itsdev.devjobcollector.ai.OpenAiEvidenceClient;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/jobs/{id}/ai-fields")
public class JobFieldAiController {
    private final JobFieldAiProcessor processor;
    private final JobPostRepository jobs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JobFieldAiController(JobFieldAiProcessor processor, JobPostRepository jobs, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.processor = processor; this.jobs = jobs; this.jdbc = jdbc; this.mapper = mapper;
    }

    @PostMapping("/analyze")
    public Map<String, String> analyze(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        authorize(actor); return Map.of("status", processor.process(id).name());
    }
    @GetMapping("/history")
    public List<Map<String, Object>> history(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        authorize(actor);
        return jdbc.queryForList("SELECT id,job_version,input_hash,policy_version,fields_json,review_status,reviewed_by,reviewed_at,created_at FROM job_field_ai_proposals WHERE job_post_id=? ORDER BY id DESC LIMIT 20", id);
    }
    @Transactional
    @PostMapping("/{proposalId}/{action}")
    public Map<String, String> review(@PathVariable long id, @PathVariable long proposalId, @PathVariable String action,
            @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal AdminPrincipal actor) {
        authorize(actor);
        if (!List.of("approve", "reject").contains(action)) throw error(HttpStatus.BAD_REQUEST, "INVALID_FIELD_ACTION");
        var job = jobs.findByIdForModeration(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND"));
        if (job.getVersion() != request.expectedVersion()) throw error(HttpStatus.CONFLICT, "JOB_CHANGED");
        var rows = jdbc.queryForList("SELECT job_version,input_hash,policy_version,model,fields_json,review_status FROM job_field_ai_proposals WHERE id=? AND job_post_id=? FOR UPDATE", proposalId, id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "FIELD_PROPOSAL_NOT_FOUND");
        var row = rows.getFirst();
        if (!"PENDING_REVIEW".equals(row.get("review_status")) || !JobFieldAiPolicy.VERSION.equals(row.get("policy_version"))
                || !OpenAiEvidenceClient.CONTENT_MODEL.equals(row.get("model"))
                || ((Number) row.get("job_version")).longValue() != request.expectedVersion() || !request.inputHash().equals(row.get("input_hash")))
            throw error(HttpStatus.CONFLICT, "FIELD_PROPOSAL_STALE");
        if ("approve".equals(action)) {
            try {
                var input = JobFieldAiProcessor.prepareMatching(job, request.inputHash());
                var fields = JobFieldAiPolicy.validate(mapper.readTree((String) row.get("fields_json")), input.evidenceSource());
                if (fields.isEmpty()) throw new IllegalArgumentException("EMPTY_FIELDS");
            } catch (Exception invalid) { throw error(HttpStatus.CONFLICT, "FIELD_PROPOSAL_STALE"); }
        }
        String status = "approve".equals(action) ? "APPROVED" : "REJECTED";
        if (jdbc.update("UPDATE job_field_ai_proposals SET review_status=?,reviewed_by=?,reviewed_at=NOW(6) WHERE id=? AND job_post_id=? AND review_status='PENDING_REVIEW'", status, actor.id(), proposalId, id) != 1)
            throw error(HttpStatus.CONFLICT, "FIELD_PROPOSAL_STALE");
        return Map.of("status", status);
    }
    private static void authorize(AdminPrincipal actor) {
        if (actor == null || actor.id() == null || (actor.role() != AdminRole.ADMIN && actor.role() != AdminRole.SUPER_ADMIN))
            throw error(HttpStatus.FORBIDDEN, "ADMIN_ROLE_DENIED");
    }
    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    public record ReviewRequest(@NotNull Long expectedVersion, @NotBlank @Pattern(regexp="[a-f0-9]{64}") String inputHash) {}
}
