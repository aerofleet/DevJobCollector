package kr.itsdev.devjobcollector.admin.jobs;

import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs/{id}/stripe-salary")
@RequiredArgsConstructor
public class StripeSalaryPreviewController {
    private final StripeSalaryPreviewService service;

    @GetMapping("/preview")
    public ResponseEntity<StripeSalaryPreviewService.Preview> preview(@PathVariable long id, @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(id, actor));
    }

}
