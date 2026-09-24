package kr.itsdev.devjobcollector.admin.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAuthenticationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        AdminSecurityConfiguration.class, AdminTokenCodec.class})
class AdminAuthenticationControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired AdminTokenCodec tokenCodec;

    @MockitoBean AdminAuthenticationService authenticationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void rejectsLoginWithoutApprovedOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"strong-password-value","mfaCode":"123456"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ORIGIN_DENIED"));

        verify(authenticationService, never()).login(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void issuesSecureSessionAndCsrfCookiesAfterMfaLogin() throws Exception {
        AdminPrincipal principal = principal("csrf-hash");
        when(authenticationService.login(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(
                AdminLoginResult.success("session-token", "csrf-token", principal));

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .header("Origin", "https://djc-admin.itsdev.kr")
                        .header("User-Agent", "security-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"strong-password-value","mfaCode":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("DJC_ADMIN_SESSION", true))
                .andExpect(cookie().secure("DJC_ADMIN_SESSION", true))
                .andExpect(cookie().value("DJC_ADMIN_CSRF", "csrf-token"))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));
    }

    @Test
    void generalMemberBearerTokenCannotAuthenticateAdminMe() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me")
                        .header("Authorization", "Bearer general-member-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtTokenVerifier);
    }

    @Test
    void returnsAdminForValidDedicatedSession() throws Exception {
        AdminPrincipal principal = principal(tokenCodec.hash("csrf-token"));
        when(authenticationService.authenticate("session-token")).thenReturn(principal);

        mockMvc.perform(get("/api/v1/admin/me")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));
    }

    private AdminPrincipal principal(String csrfHash) {
        return new AdminPrincipal(7L, "admin@example.com", "관리자", AdminRole.SUPER_ADMIN,
                Set.of(), "session-id", csrfHash);
    }
}
