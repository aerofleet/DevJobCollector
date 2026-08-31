package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

class CommonOAuth2UserServiceTest {

    @Test
    void normalizesUnexpectedUserProcessingFailure() {
        SocialUserUpsertService upsertService = mock(SocialUserUpsertService.class);
        @SuppressWarnings("unchecked")
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock(OAuth2UserService.class);
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        IllegalStateException cause = new IllegalStateException("sensitive detail");
        when(delegate.loadUser(request)).thenThrow(cause);

        CommonOAuth2UserService service = new CommonOAuth2UserService(upsertService, delegate);

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getError().getErrorCode())
                            .isEqualTo("OAUTH_LOGIN_FAILED");
                    org.assertj.core.api.Assertions.assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    @Test
    void preservesExpectedOAuthFailure() {
        SocialUserUpsertService upsertService = mock(SocialUserUpsertService.class);
        @SuppressWarnings("unchecked")
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock(OAuth2UserService.class);
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        OAuth2AuthenticationException expected = new OAuth2AuthenticationException(
                new OAuth2Error("ACCOUNT_LINK_REQUIRED")
        );
        when(delegate.loadUser(request)).thenThrow(expected);

        CommonOAuth2UserService service = new CommonOAuth2UserService(upsertService, delegate);

        assertThatThrownBy(() -> service.loadUser(request)).isSameAs(expected);
    }
}
