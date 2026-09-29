package kr.itsdev.devjobcollector.admin.accounts;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminAuthenticationService;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminSecurityConfiguration;
import kr.itsdev.devjobcollector.admin.auth.AdminTokenCodec;
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

@WebMvcTest(AdminAccountManagementController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        AdminSecurityConfiguration.class, AdminTokenCodec.class})
class AdminAccountManagementControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminTokenCodec tokenCodec;
    @MockitoBean AdminAccountManagementService service;
    @MockitoBean AdminAuthenticationService authenticationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void rejectsMemberBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/admins")
                        .header("Authorization", "Bearer member-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jwtTokenVerifier, service);
    }

    @Test
    void rejectsMutationWithoutCsrf() throws Exception {
        when(authenticationService.authenticate("admin-token")).thenReturn(superAdmin());
        mockMvc.perform(patch("/api/v1/admin/admins/2/status")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                        .header("Origin", "https://djc-admin.itsdev.kr")
                        .contentType("application/json")
                        .content("""
                                {"status":"DISABLED","expectedVersion":0,"reason":"policy"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_CSRF_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void requiresExpectedVersionForStatusChange() throws Exception {
        when(authenticationService.authenticate("admin-token")).thenReturn(superAdmin());
        mockMvc.perform(patch("/api/v1/admin/admins/2/status")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                        .header("Origin", "https://djc-admin.itsdev.kr")
                        .header("X-CSRF-TOKEN", "csrf-token")
                        .contentType("application/json")
                        .content("""
                                {"status":"DISABLED","reason":"policy"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private AdminPrincipal superAdmin() {
        return new AdminPrincipal(1L, "root@example.com", "Root", AdminRole.SUPER_ADMIN,
                Set.of(), "session", tokenCodec.hash("csrf-token"));
    }
}
