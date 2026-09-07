package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import kr.itsdev.devjobcollector.company.CompanySignupFacade;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
    private final CompanySignupFacade signupFacade;
    private final CompanyVerificationService verificationService;

    public CompanyController(CompanySignupFacade signupFacade,
                             CompanyVerificationService verificationService) {
        this.signupFacade = signupFacade;
        this.verificationService = verificationService;
    }

    @PostMapping
    public ResponseEntity<CompanySignupResponse> signup(
            @AuthenticationPrincipal String subject,
            @Valid @RequestBody CompanySignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signupFacade.signup(subject, request));
    }

    @PostMapping("/{companyId}/verification-requests")
    public ResponseEntity<CompanyVerificationResponse> submitVerification(
            @AuthenticationPrincipal String subject,
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyVerificationSubmitRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(verificationService.submit(subject, companyId, request));
    }
}
