package kr.itsdev.devjobcollector.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import kr.itsdev.devjobcollector.company.CompanyMemberRole;
import kr.itsdev.devjobcollector.company.CompanyMemberStatus;
import kr.itsdev.devjobcollector.company.CompanyAlreadyExistsException;
import kr.itsdev.devjobcollector.company.CompanySignupFacade;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CompanyController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CompanyControllerSecurityTest {
    @Autowired MockMvc mockMvc;

    @MockitoBean CompanySignupFacade signupFacade;
    @MockitoBean CompanyVerificationService verificationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @BeforeEach
    void validToken() {
        DecodedJWT jwt = org.mockito.Mockito.mock(DecodedJWT.class);
        Claim roleClaim = org.mockito.Mockito.mock(Claim.class);
        when(jwtTokenVerifier.verify("valid-token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn("USER");
    }

    @Test
    void rejectsCompanySignupWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(signupFacade);
    }

    @Test
    void createsCompanyForAuthenticatedMember() throws Exception {
        when(signupFacade.signup(org.mockito.ArgumentMatchers.eq("42"), any(CompanySignupRequest.class)))
                .thenReturn(new CompanySignupResponse(
                        7L, CompanyStatus.PENDING_VERIFICATION, 11L,
                        CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(7))
                .andExpect(jsonPath("$.companyStatus").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));

        verify(signupFacade).signup(org.mockito.ArgumentMatchers.eq("42"), any(CompanySignupRequest.class));
    }

    @Test
    void rejectsMalformedBusinessNumberBeforeFacade() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName": "테스트 주식회사",
                                  "displayName": "테스트",
                                  "businessNumber": "123-AB-67890",
                                  "websiteUrl": "https://example.com"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(signupFacade);
    }

    @Test
    void returnsStableConflictWithoutExposingBusinessNumber() throws Exception {
        when(signupFacade.signup(org.mockito.ArgumentMatchers.eq("42"), any(CompanySignupRequest.class)))
                .thenThrow(new CompanyAlreadyExistsException());

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 등록된 사업자번호입니다."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("123-45-67890"))));
    }

    @Test
    void submitsVerificationForAuthenticatedMemberWithoutReturningEvidenceKey() throws Exception {
        when(verificationService.submit(
                org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.eq(7L),
                any(CompanyVerificationSubmitRequest.class)))
                .thenReturn(new CompanyVerificationResponse(
                        9L, 7L, CompanyVerificationStatus.PENDING,
                        CompanyStatus.PENDING_VERIFICATION,
                        LocalDateTime.of(2026, 9, 8, 10, 0), null));

        mockMvc.perform(post("/api/v1/companies/7/verification-requests")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "BUSINESS_REGISTRATION_DOCUMENT",
                                  "evidenceObjectKey": "company-verification/7/evidence.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(9))
                .andExpect(jsonPath("$.requestStatus").value("PENDING"))
                .andExpect(jsonPath("$.evidenceObjectKey").doesNotExist());
    }

    private String validBody() {
        return """
                {
                  "legalName": "테스트 주식회사",
                  "displayName": "테스트",
                  "businessNumber": "123-45-67890",
                  "websiteUrl": "https://example.com"
                }
                """;
    }
}
