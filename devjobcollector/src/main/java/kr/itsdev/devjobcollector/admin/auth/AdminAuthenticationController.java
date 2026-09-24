package kr.itsdev.devjobcollector.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthenticationController {
    private final AdminAuthenticationService authenticationService;
    private final AdminSecurityProperties properties;

    public AdminAuthenticationController(AdminAuthenticationService authenticationService,
                                         AdminSecurityProperties properties) {
        this.authenticationService = authenticationService;
        this.properties = properties;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest body,
                                     HttpServletRequest request, HttpServletResponse response) {
        String requestId = requestId(request);
        AdminLoginResult result = authenticationService.login(body.email(), body.password(),
                body.mfaCode(), requestId, clientIp(request), request.getHeader("User-Agent"));
        if (result.status() != AdminLoginResult.Status.SUCCESS) throw failure(result.status());
        addCookies(response, result.sessionToken(), result.csrfToken(), properties.getSessionDuration());
        return Map.of("data", AdminResponse.from(result.principal()), "requestId", requestId);
    }

    @PostMapping("/auth/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = AdminSessionAuthenticationFilter.cookieValue(
                request, properties.getSessionCookieName());
        authenticationService.logout(sessionToken, requestId(request), clientIp(request),
                request.getHeader("User-Agent"));
        addCookies(response, "", "", Duration.ZERO);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AdminPrincipal principal,
                                  HttpServletRequest request) {
        return Map.of("data", AdminResponse.from(principal), "requestId", requestId(request));
    }

    private void addCookies(HttpServletResponse response, String sessionToken, String csrfToken,
                            Duration maxAge) {
        ResponseCookie sessionCookie = ResponseCookie.from(properties.getSessionCookieName(), sessionToken)
                .httpOnly(true).secure(properties.isSecureCookies()).sameSite("Strict")
                .path("/api/v1/admin").maxAge(maxAge).build();
        ResponseCookie.ResponseCookieBuilder csrfBuilder = ResponseCookie
                .from(properties.getCsrfCookieName(), csrfToken)
                .httpOnly(false).secure(properties.isSecureCookies()).sameSite("Strict")
                .path("/").maxAge(maxAge);
        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            csrfBuilder.domain(properties.getCookieDomain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfBuilder.build().toString());
    }

    private AdminAuthenticationException failure(AdminLoginResult.Status status) {
        return switch (status) {
            case LOCKED, DISABLED -> new AdminAuthenticationException(
                    HttpStatus.LOCKED, "ADMIN_ACCOUNT_LOCKED");
            case MFA_NOT_CONFIGURED -> new AdminAuthenticationException(
                    HttpStatus.FORBIDDEN, "ADMIN_MFA_NOT_CONFIGURED");
            default -> new AdminAuthenticationException(
                    HttpStatus.UNAUTHORIZED, "ADMIN_AUTHENTICATION_FAILED");
        };
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(AdminRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
                               @NotBlank @Size(max = 128) String password,
                               @NotBlank @Pattern(regexp = "\\d{6}") String mfaCode) {}

    public record AdminResponse(Long id, String email, String name, String role,
                                java.util.Set<String> permissions) {
        static AdminResponse from(AdminPrincipal principal) {
            return new AdminResponse(principal.id(), principal.email(), principal.name(),
                    principal.role().name(), principal.permissions().stream()
                    .map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
    }
}
