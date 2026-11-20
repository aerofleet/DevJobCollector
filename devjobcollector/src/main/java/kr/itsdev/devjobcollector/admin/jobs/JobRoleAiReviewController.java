package kr.itsdev.devjobcollector.admin.jobs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs/{id}/ai-role")
public class JobRoleAiReviewController {
    private final JobRoleAiReviewService service;
    public JobRoleAiReviewController(JobRoleAiReviewService service) { this.service = service; }

    @GetMapping("/history")
    public List<Map<String, Object>> history(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        return service.history(id, actor);
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        return service.analyze(id, actor);
    }

    @PostMapping("/{proposalId}/approve")
    public Map<String, Object> approve(@PathVariable long id, @PathVariable long proposalId,
            @Valid @RequestBody ApproveRequest request, @AuthenticationPrincipal AdminPrincipal actor) {
        return service.approve(id, proposalId, request.expectedVersion(), request.inputHash(), actor);
    }

    @PostMapping("/{proposalId}/reject")
    public Map<String, Object> reject(@PathVariable long id, @PathVariable long proposalId,
            @Valid @RequestBody ApproveRequest request, @AuthenticationPrincipal AdminPrincipal actor) {
        return service.reject(id, proposalId, request.expectedVersion(), request.inputHash(), actor);
    }

    public record ApproveRequest(@NotNull Long expectedVersion, @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String inputHash) {}
}
