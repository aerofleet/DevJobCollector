package kr.itsdev.devjobcollector.admin.companies;

import jakarta.servlet.http.Cookie;
import java.util.List;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDiscoveryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AdminSecurityConfiguration.class, AdminTokenCodec.class})
class AdminDiscoveryControllerSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AdminTokenCodec tokenCodec;
    @MockitoBean AdminDiscoveryService service;
    @MockitoBean AdminAuthenticationService authenticationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void requiresAdminSessionAndDisablesCaching() throws Exception {
        String path = "/api/v1/admin/companies/discovery-candidates";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        when(authenticationService.authenticate("token")).thenReturn(
                new AdminPrincipal(1L, "admin@example.com", "Admin", AdminRole.REVIEWER,
                        Set.of(), "session", "csrf"));
        when(service.list(null, 0, 20)).thenReturn(List.of());
        mvc.perform(get(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void rejectRequiresAdminSessionAndCsrf() throws Exception {
        String path = "/api/v1/admin/companies/discovery-candidates/1/reject";
        mvc.perform(post(path).contentType("application/json").content("{\"reason\":\"검수\"}"))
                .andExpect(status().isForbidden());
        when(authenticationService.authenticate("token")).thenReturn(
                new AdminPrincipal(1L, "admin@example.com", "Admin", AdminRole.REVIEWER,
                        Set.of(), "session", tokenCodec.hash("csrf")));
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                        .header("Origin", "https://djc-admin.itsdev.kr")
                        .contentType("application/json").content("{\"reason\":\"검수\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(post(path).cookie(new Cookie("DJC_ADMIN_SESSION", "token"))
                        .header("Origin", "https://djc-admin.itsdev.kr").header("X-CSRF-TOKEN", "csrf")
                        .contentType("application/json").content("{\"reason\":\"검수\"}"))
                .andExpect(status().isNoContent());
    }
}
