package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import kr.itsdev.auth.common.config.AuthCommonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

class OAuth2CallbackExceptionFilterTest {

    @Test
    void routesUnexpectedCallbackExceptionThroughFailureHandler() throws Exception {
        AuthCommonProperties properties = new AuthCommonProperties();
        properties.setFrontendFailureUri("https://frontend.example/oauth/callback");
        AuthenticationFailureHandler failureHandler = new SocialLoginFailureHandler(properties);
        OAuth2CallbackExceptionFilter filter = new OAuth2CallbackExceptionFilter(failureHandler);
        MockHttpServletRequest request = callbackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new ServletException("provider detail must stay hidden");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example/oauth/callback?error=OAUTH_LOGIN_FAILED&provider=github");
        assertThat(response.getRedirectedUrl()).doesNotContain("provider detail");
    }

    @Test
    void returnsSafeJsonWhenFailureHandlerAlsoFails() throws Exception {
        AuthenticationFailureHandler failureHandler = new ThrowingFailureHandler();
        OAuth2CallbackExceptionFilter filter = new OAuth2CallbackExceptionFilter(failureHandler);
        MockHttpServletRequest request = callbackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new IllegalStateException("token must stay hidden");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"OAUTH_LOGIN_FAILED\"}");
        assertThat(response.getContentAsString()).doesNotContain("token must stay hidden");
    }

    @Test
    void doesNotInterceptNonCallbackRequest() throws Exception {
        OAuth2CallbackExceptionFilter filter = new OAuth2CallbackExceptionFilter(
                (request, response, exception) -> response.setStatus(401)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> response.setStatus(204);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(204);
    }

    private MockHttpServletRequest callbackRequest() {
        return new MockHttpServletRequest("GET", "/login/oauth2/code/github");
    }

    private static class ThrowingFailureHandler implements AuthenticationFailureHandler {
        @Override
        public void onAuthenticationFailure(
                jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response,
                AuthenticationException exception
        ) throws IOException, ServletException {
            throw new ServletException("handler detail must stay hidden");
        }
    }
}
