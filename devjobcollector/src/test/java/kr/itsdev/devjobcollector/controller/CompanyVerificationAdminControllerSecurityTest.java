package kr.itsdev.devjobcollector.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyVerificationException;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
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

@WebMvcTest(CompanyVerificationAdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CompanyVerificationAdminControllerSecurityTest {
    @Autowired MockMvc mockMvc;

    @MockitoBean CompanyVerificationService verificationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @BeforeEach
    void validToken() {
        DecodedJWT jwt = org.mockito.Mockito.mock(DecodedJWT.class);
        Claim roleClaim = org.mockito.Mockito.mock(Claim.class);
        when(jwtTokenVerifier.verify("valid-token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("20");
        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn("PLATFORM_ADMIN");
    }

    @Test
    void rejectsAnonymousApproval() throws Exception {
        mockMvc.perform(post("/api/v1/admin/company-verification-requests/5/approve"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(verificationService);
    }

    @Test
    void approvesAsAuthenticatedPlatformAdmin() throws Exception {
        when(verificationService.approve("20", 5L)).thenReturn(response(CompanyVerificationStatus.APPROVED));

        mockMvc.perform(post("/api/v1/admin/company-verification-requests/5/approve")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("APPROVED"))
                .andExpect(jsonPath("$.companyStatus").value("VERIFIED"));
    }

    @Test
    void returnsStableForbiddenCodeForRegularUser() throws Exception {
        when(verificationService.approve("20", 5L))
                .thenThrow(CompanyVerificationException.platformAdminRequired());

        mockMvc.perform(post("/api/v1/admin/company-verification-requests/5/approve")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_ADMIN_REQUIRED"));
    }

    @Test
    void rejectsBlankRejectionReasonBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/admin/company-verification-requests/5/reject")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verificationService);
    }

    @Test
    void rejectsWithAuditableReason() throws Exception {
        when(verificationService.reject(eq("20"), eq(5L), eq("서류 식별 불가")))
                .thenReturn(response(CompanyVerificationStatus.REJECTED));

        mockMvc.perform(post("/api/v1/admin/company-verification-requests/5/reject")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"서류 식별 불가\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("REJECTED"));
    }

    private CompanyVerificationResponse response(CompanyVerificationStatus status) {
        return new CompanyVerificationResponse(
                5L, 7L, status,
                status == CompanyVerificationStatus.APPROVED
                        ? CompanyStatus.VERIFIED : CompanyStatus.REJECTED,
                LocalDateTime.of(2026, 9, 8, 9, 0),
                LocalDateTime.of(2026, 9, 8, 10, 0));
    }
}
