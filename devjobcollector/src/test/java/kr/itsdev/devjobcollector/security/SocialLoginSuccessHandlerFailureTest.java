package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import kr.itsdev.auth.common.config.AuthCommonProperties;
import kr.itsdev.auth.common.oauth.AuthCommonAttributeKeys;
import kr.itsdev.auth.common.oauth.SocialLoginSuccessHandler;
import kr.itsdev.auth.common.spi.TokenIssueService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class SocialLoginSuccessHandlerFailureTest {

    @Test
    void redirectsUnexpectedTokenIssueFailureToFrontendError() throws Exception {
        AuthCommonProperties properties = new AuthCommonProperties();
        properties.setFrontendFailureUri("https://frontend.example/oauth/callback");
        TokenIssueService tokenIssueService = mock(TokenIssueService.class);
        when(tokenIssueService.issueAccessToken(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("sensitive detail"));
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "id", "provider-subject",
                        AuthCommonAttributeKeys.APP_USER_ID, 1L,
                        AuthCommonAttributeKeys.APP_USER_EMAIL, "member@example.com",
                        AuthCommonAttributeKeys.APP_USER_NAME, "member",
                        AuthCommonAttributeKeys.APP_USER_ROLE, "USER"
                ),
                "id"
        );
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SocialLoginSuccessHandler(properties, tokenIssueService).onAuthenticationSuccess(
                new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example/oauth/callback?error=OAUTH_LOGIN_FAILED");
    }
}
