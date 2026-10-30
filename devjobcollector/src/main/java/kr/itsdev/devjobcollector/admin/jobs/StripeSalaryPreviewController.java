package kr.itsdev.devjobcollector.admin.jobs;

import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs/{id}/stripe-salary")
@RequiredArgsConstructor
public class StripeSalaryPreviewController {
    private final StripeSalaryPreviewService service;
    private final StripeSalaryApplyService applyService;

    @GetMapping("/preview")
    public ResponseEntity<StripeSalaryPreviewService.Preview> preview(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(id, actor));
    }

    @PostMapping("/apply")
    public Map<String, Object> apply(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor,
                                     @Valid @RequestBody ApplyRequest input, HttpServletRequest request) {
        return applyService.apply(id, input.expectedVersion(), input.sourceHash(), actor,
                (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE));
    }

    public record ApplyRequest(@NotNull Long expectedVersion, @NotNull @Pattern(regexp = "[a-f0-9]{64}") String sourceHash) { }
}
