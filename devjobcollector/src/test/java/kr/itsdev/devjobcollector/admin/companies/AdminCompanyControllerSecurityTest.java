package kr.itsdev.devjobcollector.admin.companies;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.auth.AdminAuthenticationService;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminSecurityConfiguration;
import kr.itsdev.devjobcollector.admin.auth.AdminTokenCodec;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCompanyController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        AdminSecurityConfiguration.class, AdminTokenCodec.class})
class AdminCompanyControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminTokenCodec tokenCodec;
    @MockitoBean AdminCompanyService companyService;
    @MockitoBean AdminCompanyReviewService reviewService;
    @MockitoBean CompanyEvidenceService evidenceService;
    @MockitoBean AdminAuditLogRepository audit;
    @MockitoBean AdminAuthenticationService authenticationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void evidenceRequiresAdminSessionAndIsNeverCached() throws Exception {
        mockMvc.perform(get("/api/v1/admin/companies/7/verification-requests/9/evidence"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(evidenceService);

        when(authenticationService.authenticate("admin-token")).thenReturn(admin());
        when(evidenceService.readForAdminCompany(7L, 9L))
                .thenReturn(new CompanyEvidenceService.Evidence("image/png", new byte[] {1, 2, 3}));
        mockMvc.perform(get("/api/v1/admin/companies/7/verification-requests/9/evidence")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=business-registration.png"));
    }

    @Test
    void approvalRequiresOriginAndCsrf() throws Exception {
        when(authenticationService.authenticate("admin-token")).thenReturn(admin());
        mockMvc.perform(post("/api/v1/admin/companies/7/verification-requests/9/approve")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                        .header("Origin", "https://djc-admin.itsdev.kr"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reviewService);

        var detail = org.mockito.Mockito.mock(AdminCompanyService.CompanyDetail.class);
        when(reviewService.review(eq(7L), eq(9L), eq(true), eq(null), any(), any(), any(),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(detail);
        mockMvc.perform(post("/api/v1/admin/companies/7/verification-requests/9/approve")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                        .header("Origin", "https://djc-admin.itsdev.kr")
                        .header("X-CSRF-TOKEN", "csrf-token"))
                .andExpect(status().isOk());
    }

    private AdminPrincipal admin() {
        return new AdminPrincipal(1L, "admin@example.com", "Admin", AdminRole.REVIEWER,
                Set.of(), "session", tokenCodec.hash("csrf-token"));
    }
}
