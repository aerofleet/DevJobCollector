package kr.itsdev.devjobcollector.admin.dashboard;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        AdminSecurityConfiguration.class, AdminTokenCodec.class})
class AdminDashboardControllerSecurityTest {
    @Autowired MockMvc mockMvc;

    @MockitoBean AdminDashboardService dashboardService;
    @MockitoBean AdminAuthenticationService authenticationService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void rejectsGeneralMemberBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", "Bearer general-member-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtTokenVerifier, dashboardService);
    }

    @Test
    void returnsSummaryForDedicatedAdminSession() throws Exception {
        AdminPrincipal principal = new AdminPrincipal(7L, "admin@example.com", "관리자",
                AdminRole.ADMIN, Set.of(), "session-id", "a".repeat(64));
        when(authenticationService.authenticate("admin-session-token")).thenReturn(principal);
        when(dashboardService.summary()).thenReturn(summary());

        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .cookie(new Cookie("DJC_ADMIN_SESSION", "admin-session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.metrics.totalUsers.value").value(1280))
                .andExpect(jsonPath("$.data.signupTrend.length()").value(7))
                .andExpect(jsonPath("$.data.reviewQueue.pendingCompanyVerifications").value(7));
    }

    private AdminDashboardSummary summary() {
        var trend = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> new AdminDashboardSummary.DailySignupPoint(
                        java.time.LocalDate.of(2026, 9, 19).plusDays(index), index))
                .toList();
        return new AdminDashboardSummary(
                OffsetDateTime.parse("2026-09-25T09:30:00+09:00"), "Asia/Seoul",
                Map.of("totalUsers", AdminDashboardSummary.Metric.available(
                        1_280L, "전체 일반 회원")), trend,
                new AdminDashboardSummary.ReviewQueue(7L));
    }
}
