package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import kr.itsdev.devjobcollector.company.CompanySignupFacade;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
    private final CompanySignupFacade signupFacade;

    public CompanyController(CompanySignupFacade signupFacade) {
        this.signupFacade = signupFacade;
    }

    @PostMapping
    public ResponseEntity<CompanySignupResponse> signup(
            @AuthenticationPrincipal String subject,
            @Valid @RequestBody CompanySignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signupFacade.signup(subject, request));
    }
}
