package kr.itsdev.devjobcollector.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.dto.auth.LoginRequest;
import kr.itsdev.devjobcollector.dto.auth.LoginResponse;
import kr.itsdev.devjobcollector.dto.auth.AccountLinkStartResponse;
import kr.itsdev.devjobcollector.dto.auth.PersonalSignupRequest;
import kr.itsdev.devjobcollector.dto.auth.PersonalSignupResponse;
import kr.itsdev.devjobcollector.dto.auth.ResendVerificationRequest;
import kr.itsdev.devjobcollector.dto.auth.VerifyEmailRequest;
import kr.itsdev.devjobcollector.security.service.LocalCredentialAuthService;
import kr.itsdev.devjobcollector.security.service.AccountLinkService;
import kr.itsdev.devjobcollector.security.signup.PersonalSignupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LocalCredentialAuthService localCredentialAuthService;
    private final TokenIssueService tokenIssueService;
    private final PersonalSignupService personalSignupService;
    private final AccountLinkService accountLinkService;

    public AuthController(
            LocalCredentialAuthService localCredentialAuthService,
            TokenIssueService tokenIssueService,
            PersonalSignupService personalSignupService,
            AccountLinkService accountLinkService
    ) {
        this.localCredentialAuthService = localCredentialAuthService;
        this.tokenIssueService = tokenIssueService;
        this.personalSignupService = personalSignupService;
        this.accountLinkService = accountLinkService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AuthenticatedUser user = localCredentialAuthService.authenticate(request.identifier(), request.password());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String accessToken = tokenIssueService.issueAccessToken(user);
        return ResponseEntity.ok(new LoginResponse(accessToken, "Bearer"));
    }

    @PostMapping("/signup/personal")
    public ResponseEntity<PersonalSignupResponse> signupPersonal(
            @Valid @RequestBody PersonalSignupRequest request,
            HttpServletRequest servletRequest
    ) {
        PersonalSignupResponse response = personalSignupService.signup(request, clientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/signup/personal/verify-email")
    public ResponseEntity<LoginResponse> verifyPersonalEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(personalSignupService.verifyEmail(request.email(), request.code()));
    }

    @PostMapping("/signup/personal/resend")
    public ResponseEntity<PersonalSignupResponse> resendPersonalVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(personalSignupService.resend(
                request.email(), request.turnstileToken(), clientIp(servletRequest)));
    }

    @PostMapping("/account-links/{provider}/start")
    public AccountLinkStartResponse startAccountLink(
            @AuthenticationPrincipal String subject,
            @PathVariable String provider,
            Authentication authentication,
            HttpServletRequest request
    ) {
        java.time.Instant authenticatedAt = authentication.getDetails() instanceof java.time.Instant instant
                ? instant
                : null;
        return accountLinkService.start(subject, provider, authenticatedAt, request);
    }

    private String clientIp(HttpServletRequest request) {
        String cloudflareIp = request.getHeader("CF-Connecting-IP");
        if (cloudflareIp != null && !cloudflareIp.isBlank()) {
            return cloudflareIp.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
