package kr.itsdev.devjobcollector.controller;

import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.dto.auth.LoginRequest;
import kr.itsdev.devjobcollector.dto.auth.LoginResponse;
import kr.itsdev.devjobcollector.security.service.LocalCredentialAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LocalCredentialAuthService localCredentialAuthService;
    private final TokenIssueService tokenIssueService;

    public AuthController(
            LocalCredentialAuthService localCredentialAuthService,
            TokenIssueService tokenIssueService
    ) {
        this.localCredentialAuthService = localCredentialAuthService;
        this.tokenIssueService = tokenIssueService;
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
}
