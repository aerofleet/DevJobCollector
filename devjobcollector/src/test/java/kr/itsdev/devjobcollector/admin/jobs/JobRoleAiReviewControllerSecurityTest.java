package kr.itsdev.devjobcollector.admin.jobs;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@WebMvcTest(JobRoleAiReviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AdminSecurityConfiguration.class, AdminTokenCodec.class})
class JobRoleAiReviewControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JobRoleAiReviewService service;
    @MockitoBean AdminAuthenticationService authentication;
    @MockitoBean JwtTokenVerifier verifier;
    @MockitoBean PerfLogProperties performance;

    @Test void anonymousCannotReadOrApprove() throws Exception {
        mvc.perform(get("/api/v1/admin/jobs/13758/ai-role/history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/jobs/13758/ai-role/analyze")
                .header("Origin", "https://djc-admin.itsdev.kr")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/jobs/13758/ai-role/4/approve")
                .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json")
                .content("{\"inputHash\":\"" + "a".repeat(64) + "\",\"expectedVersion\":0}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void authenticatedAdministratorCannotApproveOrRejectWithoutCsrf() throws Exception {
        when(authentication.authenticate("admin-token")).thenReturn(new AdminPrincipal(
                1L, "admin@example.com", "admin", AdminRole.ADMIN, Set.of(), "session", "hash"));
        for (String action : new String[] {"approve", "reject"}) {
            mvc.perform(post("/api/v1/admin/jobs/13758/ai-role/4/" + action)
                    .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                    .header("Origin", "https://djc-admin.itsdev.kr").contentType("application/json")
                    .content("{\"inputHash\":\"" + "a".repeat(64) + "\",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/admin/jobs/13758/ai-role/analyze")
                .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-token"))
                .header("Origin", "https://djc-admin.itsdev.kr")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
