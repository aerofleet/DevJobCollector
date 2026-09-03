package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;

import kr.itsdev.auth.common.config.AuthCommonProperties;
import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import kr.itsdev.auth.common.oauth.SocialLoginFailureHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;

class SocialLoginFailureHandlerTest {

    @Test
    void redirectsAccountLinkConflictWithStableErrorCode() throws Exception {
        AuthCommonProperties properties = new AuthCommonProperties();
        properties.setFrontendFailureUri("https://frontend.example/oauth/callback?source=oauth");
        SocialLoginFailureHandler handler = new SocialLoginFailureHandler(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/login/oauth2/code/google");
        handler.onAuthenticationFailure(request, response, new AccountLinkRequiredException());

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "https://frontend.example/oauth/callback?source=oauth&error=ACCOUNT_LINK_REQUIRED&provider=google");
    }

    @Test
    void hidesUnexpectedAuthenticationFailureDetails() throws Exception {
        AuthCommonProperties properties = new AuthCommonProperties();
        SocialLoginFailureHandler handler = new SocialLoginFailureHandler(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest(), response,
                new AuthenticationServiceException("provider token leaked here"));

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "http://localhost:5173/oauth/callback?error=OAUTH_LOGIN_FAILED");
        assertThat(response.getRedirectedUrl()).doesNotContain("provider token leaked here");
    }
}
