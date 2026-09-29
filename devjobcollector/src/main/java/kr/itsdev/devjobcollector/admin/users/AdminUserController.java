package kr.itsdev.devjobcollector.admin.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) UserAccountStatus status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    HttpServletRequest request) {
        Page<AdminUserService.UserView> result = service.list(keyword, status, page, size);
        return Map.of("data", result, "requestId", requestId(request));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, HttpServletRequest request) {
        return Map.of("data", service.detail(id), "requestId", requestId(request));
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> moderate(@PathVariable Long id, @Valid @RequestBody StatusRequest body,
                                        @AuthenticationPrincipal AdminPrincipal principal,
                                        HttpServletRequest request) {
        return Map.of("data", service.moderate(id, body.status(), body.expectedVersion(),
                body.reason(), principal, requestId(request), request.getRemoteAddr(),
                request.getHeader("User-Agent")), "requestId", requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
    }

    public record StatusRequest(@NotNull UserAccountStatus status, long expectedVersion,
                                @NotBlank @Size(max = 500) String reason) {}
}
