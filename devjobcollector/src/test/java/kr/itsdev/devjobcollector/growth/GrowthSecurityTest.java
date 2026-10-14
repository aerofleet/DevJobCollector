package kr.itsdev.devjobcollector.growth;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.*;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({GrowthEventController.class, GrowthDashboardController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AdminSecurityConfiguration.class, AdminTokenCodec.class})
class GrowthSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean GrowthEventService service;
    @MockitoBean AdminAuthenticationService auth;
    @MockitoBean JwtTokenVerifier jwt;
    @MockitoBean PerfLogProperties perf;
    private String event(String source, String kind) {
        return """
                {"eventId":"00000000-0000-0000-0000-000000000001","visitorId":"00000000-0000-0000-0000-000000000002",
                "sessionId":"00000000-0000-0000-0000-000000000003","kind":"%s","page":"home","source":"%s","medium":"social","campaign":"launch"}
                """.formatted(kind, source);
    }
    @Test void acceptsAnonymousBoundedEvent() throws Exception {
        mvc.perform(post("/api/v1/analytics/events").contentType("application/json").content(event("velog","PAGE_VIEW"))).andExpect(status().isNoContent());
        verify(service).record(any());
    }
    @Test void rejectsRawUrlAndEmailInAttribution() throws Exception {
        mvc.perform(post("/api/v1/analytics/events").contentType("application/json").content(event("someone@example.com","PAGE_VIEW"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsUnrecognizedEvents() throws Exception {
        mvc.perform(post("/api/v1/analytics/events").contentType("application/json").content(event("direct","PASSWORD"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void anonymousCannotReadGrowth() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/growth")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void memberJwtCannotReadGrowth() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/growth").header("Authorization","Bearer member-token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,jwt);
    }
    @Test void dedicatedAdminCanReadGrowth() throws Exception {
        when(auth.authenticate("admin-token")).thenReturn(new AdminPrincipal(7L,"admin@example.com","admin", AdminRole.ADMIN, Set.of(),"session","a".repeat(64)));
        when(service.summary()).thenReturn(Map.of("windowDays",28));
        mvc.perform(get("/api/v1/admin/dashboard/growth").cookie(new Cookie("DJC_ADMIN_SESSION","admin-token")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.windowDays").value(28));
    }
}
