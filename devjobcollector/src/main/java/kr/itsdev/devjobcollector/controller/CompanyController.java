package kr.itsdev.devjobcollector.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.itsdev.devjobcollector.company.CompanyMemberManagementService;
import kr.itsdev.devjobcollector.company.CompanyProfileService;
import kr.itsdev.devjobcollector.company.CompanySignupFacade;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberInvitationRequest;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberRoleUpdateRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupResponse;
import kr.itsdev.devjobcollector.dto.company.CompanySummaryResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
    private final CompanySignupFacade signupFacade;
    private final CompanyProfileService profileService;
    private final CompanyVerificationService verificationService;
    private final CompanyMemberManagementService memberManagementService;

    public CompanyController(CompanySignupFacade signupFacade,
                             CompanyProfileService profileService,
                             CompanyVerificationService verificationService,
                             CompanyMemberManagementService memberManagementService) {
        this.signupFacade = signupFacade;
        this.profileService = profileService;
        this.verificationService = verificationService;
        this.memberManagementService = memberManagementService;
    }

    @GetMapping("/me")
    public List<CompanySummaryResponse> getMyCompanies(
            @AuthenticationPrincipal String subject
    ) {
        return profileService.getMyCompanies(subject);
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

    @GetMapping("/{companyId}/members")
    public List<CompanyMemberResponse> listMembers(
            @AuthenticationPrincipal String subject,
            @PathVariable Long companyId
    ) {
        return memberManagementService.listMembers(subject, companyId);
    }

    @PostMapping("/{companyId}/members/invitations")
    public ResponseEntity<CompanyMemberResponse> inviteMember(
            @AuthenticationPrincipal String subject,
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyMemberInvitationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberManagementService.invite(subject, companyId, request));
    }

    @PatchMapping("/{companyId}/members/{memberId}/role")
    public CompanyMemberResponse changeMemberRole(
            @AuthenticationPrincipal String subject,
            @PathVariable Long companyId,
            @PathVariable Long memberId,
            @Valid @RequestBody CompanyMemberRoleUpdateRequest request
    ) {
        return memberManagementService.changeRole(subject, companyId, memberId, request.role());
    }

    @DeleteMapping("/{companyId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal String subject,
            @PathVariable Long companyId,
            @PathVariable Long memberId
    ) {
        memberManagementService.remove(subject, companyId, memberId);
        return ResponseEntity.noContent().build();
    }
}
