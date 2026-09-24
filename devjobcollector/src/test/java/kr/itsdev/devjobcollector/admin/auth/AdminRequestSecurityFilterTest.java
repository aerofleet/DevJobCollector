package kr.itsdev.devjobcollector.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminRequestSecurityFilterTest {
    private final AdminSecurityProperties properties = new AdminSecurityProperties();
    private final AdminTokenCodec tokenCodec = new AdminTokenCodec();
    private final AdminRequestSecurityFilter filter = new AdminRequestSecurityFilter(
            properties, tokenCodec, new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsLoginFromUnknownOriginAndSetsNoStoreHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/admin/auth/login");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ADMIN_ORIGIN_DENIED");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void acceptsMatchingCsrfTokenForAuthenticatedMutation() throws Exception {
        String csrfToken = tokenCodec.generate();
        AdminPrincipal principal = new AdminPrincipal(1L, "admin@example.com", "관리자",
                AdminRole.ADMIN, Set.of(), "session-id", tokenCodec.hash(csrfToken));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, Set.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/admin/auth/logout");
        request.addHeader("Origin", "https://djc-admin.itsdev.kr");
        request.addHeader("X-CSRF-TOKEN", csrfToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingCsrfTokenForAuthenticatedMutation() throws Exception {
        AdminPrincipal principal = new AdminPrincipal(1L, "admin@example.com", "관리자",
                AdminRole.ADMIN, Set.of(), "session-id", tokenCodec.hash("csrf-token"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, Set.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/admin/auth/logout");
        request.addHeader("Origin", "https://djc-admin.itsdev.kr");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ADMIN_CSRF_INVALID");
    }
}
