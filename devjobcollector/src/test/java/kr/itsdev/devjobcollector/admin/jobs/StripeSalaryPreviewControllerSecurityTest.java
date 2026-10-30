package kr.itsdev.devjobcollector.admin.jobs;

import kr.itsdev.devjobcollector.admin.auth.AdminAuthenticationService;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import jakarta.servlet.http.Cookie;
import java.util.Set;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeSalaryPreviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AdminSecurityConfiguration.class, AdminTokenCodec.class})
class StripeSalaryPreviewControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean StripeSalaryPreviewService service;
    @MockitoBean StripeSalaryApplyService applyService;
    @MockitoBean AdminAuthenticationService authentication;
    @MockitoBean JwtTokenVerifier verifier;
    @MockitoBean PerfLogProperties performance;

    @Test void anonymousCannotPreview() throws Exception {
        mvc.perform(get("/api/v1/admin/jobs/11051/stripe-salary/preview")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void anonymousCannotApply() throws Exception {
        mvc.perform(post("/api/v1/admin/jobs/11051/stripe-salary/apply")
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json")
                .content("{\"expectedVersion\":1,\"sourceHash\":\"" + "a".repeat(64) + "\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(applyService);
    }

    @Test void authenticatedAdminCannotApplyWithoutCsrf() throws Exception {
        when(authentication.authenticate("admin-token")).thenReturn(new AdminPrincipal(1L, "admin@example.com", "admin", AdminRole.ADMIN, Set.of(), "session", "hash"));
        mvc.perform(post("/api/v1/admin/jobs/11051/stripe-salary/apply")
                .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json")
                .content("{\"expectedVersion\":1,\"sourceHash\":\"" + "a".repeat(64) + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(applyService);
    }
}
