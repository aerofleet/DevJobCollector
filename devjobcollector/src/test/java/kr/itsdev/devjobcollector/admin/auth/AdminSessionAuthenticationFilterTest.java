package kr.itsdev.devjobcollector.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminSessionAuthenticationFilterTest {
    private final AdminAuthenticationService service = mock(AdminAuthenticationService.class);
    private final AdminSecurityProperties properties = new AdminSecurityProperties();
    private final AdminSessionAuthenticationFilter filter =
            new AdminSessionAuthenticationFilter(service, properties);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresGeneralMemberAuthorizationHeaderWithoutAdminCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/me");
        request.addHeader("Authorization", "Bearer general-member-jwt");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticatesOnlyWithValidAdminSessionCookie() throws Exception {
        AdminPrincipal principal = new AdminPrincipal(7L, "admin@example.com", "관리자",
                AdminRole.SUPER_ADMIN, Set.of(), "session-id", "a".repeat(64));
        when(service.authenticate("opaque-admin-token")).thenReturn(principal);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/me");
        request.setCookies(new Cookie(properties.getSessionCookieName(), "opaque-admin-token"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(principal);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_SUPER_ADMIN");
    }
}
