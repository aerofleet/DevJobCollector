package kr.itsdev.devjobcollector.company.jobs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/companies/{companyId}/jobs")
public class CompanyJobController {
    private final CompanyJobService service;
    public CompanyJobController(CompanyJobService service) { this.service=service; }
    public record Edit(@NotNull Long expectedVersion, @NotNull @Valid CompanyJobContent.Input content) {}
    public record Version(@NotNull Long expectedVersion) {}
    @GetMapping public Page<CompanyJobService.View> list(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @RequestParam(defaultValue="0") int page) { return service.list(subject, companyId, page); }
    @PostMapping public CompanyJobService.View create(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @Valid @RequestBody CompanyJobContent.Input content) { return service.create(subject, companyId, content); }
    @PutMapping("/{id}") public CompanyJobService.View edit(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @PathVariable Long id, @Valid @RequestBody Edit body) {
        return service.edit(subject, companyId, id, body.expectedVersion(), body.content());
    }
    @PostMapping("/{id}/submit") public CompanyJobService.View submit(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @PathVariable Long id, @Valid @RequestBody Version body) {
        return service.submit(subject, companyId, id, body.expectedVersion());
    }
    @PostMapping("/{id}/close") public CompanyJobService.View close(@AuthenticationPrincipal String subject,
            @PathVariable Long companyId, @PathVariable Long id, @Valid @RequestBody Version body) {
        return service.close(subject, companyId, id, body.expectedVersion());
    }
}
