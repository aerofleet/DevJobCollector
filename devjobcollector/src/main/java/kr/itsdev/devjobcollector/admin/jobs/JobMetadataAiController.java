package kr.itsdev.devjobcollector.admin.jobs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.List;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs/{id}/ai-metadata")
public class JobMetadataAiController {
    private final JobMetadataAiService service;
    public JobMetadataAiController(JobMetadataAiService service) { this.service = service; }

    @GetMapping("/preview")
    public JobMetadataAiService.Preview preview(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        return service.preview(id, actor);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        return service.history(id, actor);
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@PathVariable long id, @Valid @RequestBody AnalyzeRequest request,
                                       @AuthenticationPrincipal AdminPrincipal actor) {
        return service.analyze(id, request.inputHash(), request.confirmed(), actor);
    }

    public record AnalyzeRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{64}") String inputHash, boolean confirmed) {}
}
