package kr.itsdev.devjobcollector.admin.accounts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import kr.itsdev.devjobcollector.admin.AdminAccountStatus;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminRequestSecurityFilter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/admins")
public class AdminAccountManagementController {
    private final AdminAccountManagementService service;

    public AdminAccountManagementController(AdminAccountManagementService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AdminRole role,
            @RequestParam(required = false) AdminAccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return Map.of("data", service.list(principal, keyword, role, status, page, size),
                "requestId", requestId(request));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@AuthenticationPrincipal AdminPrincipal principal,
                                      @PathVariable Long id, HttpServletRequest request) {
        return Map.of("data", service.detail(principal, id), "requestId", requestId(request));
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody CreateRequest body, HttpServletRequest request) {
        return Map.of("data", service.create(principal, body.email(), body.name(), body.role(),
                body.password(), body.mfaSecret(), requestId(request), request.getRemoteAddr(),
                request.getHeader("User-Agent")), "requestId", requestId(request));
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> status(@AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody StatusRequest body,
            HttpServletRequest request) {
        return Map.of("data", service.changeStatus(principal, id, body.status(),
                body.expectedVersion(), body.reason(), requestId(request),
                request.getRemoteAddr(), request.getHeader("User-Agent")),
                "requestId", requestId(request));
    }

    @PatchMapping("/{id}/role")
    public Map<String, Object> role(@AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody RoleRequest body,
            HttpServletRequest request) {
        return Map.of("data", service.changeRole(principal, id, body.role(),
                body.expectedVersion(), body.reason(), requestId(request),
                request.getRemoteAddr(), request.getHeader("User-Agent")),
                "requestId", requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
    }

    public record CreateRequest(@NotBlank @Email @Size(max = 255) String email,
                                @NotBlank @Size(max = 100) String name,
                                @NotNull AdminRole role,
                                @NotBlank @Size(min = 16, max = 128) String password,
                                @NotBlank @Size(max = 64) String mfaSecret) {
        @Override public String toString() {
            return "CreateRequest[email=" + email + ", name=" + name + ", role=" + role
                    + ", password=<redacted>, mfaSecret=<redacted>]";
        }
    }

    public record StatusRequest(@NotNull AdminAccountStatus status,
                                @NotNull Long expectedVersion,
                                @NotBlank @Size(max = 500) String reason) {}

    public record RoleRequest(@NotNull AdminRole role, @NotNull Long expectedVersion,
                              @NotBlank @Size(max = 500) String reason) {}
}
