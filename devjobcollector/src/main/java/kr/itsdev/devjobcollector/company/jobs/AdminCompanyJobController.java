package kr.itsdev.devjobcollector.company.jobs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/jobs/company-submissions")
public class AdminCompanyJobController {
    private final CompanyJobService service;
    public AdminCompanyJobController(CompanyJobService service) { this.service=service; }
    public record Review(@NotNull Long expectedVersion, @NotBlank String action, @NotBlank @Size(max=500) String reason) {}
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="0") int page,
            @RequestParam(required=false) CompanyJob.State state) { return Map.of("data", service.reviewList(page, state)); }
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable Long id) { return Map.of("data", service.reviewDetail(id)); }
    @GetMapping("/history") public Map<String,Object> history(@RequestParam(defaultValue="0") int page,
            @RequestParam(required=false) String action, @RequestParam(required=false) Long jobId) {
        return Map.of("data", service.reviewHistory(page, action, jobId));
    }
    @PostMapping("/{id}/review") public Map<String,Object> review(@PathVariable Long id,
            @Valid @RequestBody Review body, @AuthenticationPrincipal AdminPrincipal actor) {
        return Map.of("data", service.review(id, body.expectedVersion(), body.action(), body.reason(), actor));
    }
}
