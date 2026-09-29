package kr.itsdev.devjobcollector.admin.users;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        AdminSecurityConfiguration.class, AdminTokenCodec.class})
class AdminUserControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AdminUserService service;
    @MockitoBean AdminAuthenticationService authenticationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void rejectsMemberBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer member-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jwtTokenVerifier, service);
    }

    @Test
    void listsMembersForDedicatedAdminSession() throws Exception {
        when(authenticationService.authenticate("admin-token")).thenReturn(admin());
        when(service.list(null, null, 0, 20)).thenReturn(new PageImpl<>(java.util.List.of(
                new AdminUserService.UserView(7L, "member@example.com", "Member", "USER",
                        "ACTIVE", "LOCAL", 0, LocalDateTime.of(2026, 9, 29, 9, 0)))));

        mockMvc.perform(get("/api/v1/admin/users")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value("member@example.com"));
    }

    @Test
    void rejectsStatusChangeWithoutCsrfToken() throws Exception {
        when(authenticationService.authenticate("admin-token")).thenReturn(admin());

        mockMvc.perform(patch("/api/v1/admin/users/7/status")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                        .header("Origin", "https://djc-admin.itsdev.kr")
                        .contentType("application/json")
                        .content("""
                                {"status":"SUSPENDED","expectedVersion":0,"reason":"policy"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_CSRF_INVALID"));
        verifyNoInteractions(service);
    }

    private AdminPrincipal admin() {
        return new AdminPrincipal(1L, "admin@example.com", "Admin", AdminRole.ADMIN,
                Set.of(), "session", "a".repeat(64));
    }
}
