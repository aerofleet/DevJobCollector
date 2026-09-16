package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class CommonOidcUserServiceTest {

    @Test
    void retrievesKakaoUserInfoForProviderSpecificScopes() {
        OidcUserService delegate = CommonOidcUserService.defaultOidcUserService();
        @SuppressWarnings("unchecked")
        OAuth2UserService<OAuth2UserRequest, OAuth2User> userInfoService = mock(OAuth2UserService.class);
        when(userInfoService.loadUser(any())).thenReturn(new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "sub", "kakao-subject",
                        "email", "member@example.com",
                        "email_verified", true
                ),
                "sub"
        ));
        delegate.setOauth2UserService(userInfoService);

        OidcUser result = delegate.loadUser(request(KakaoOidcProfileAdapter.ISSUER));

        verify(userInfoService).loadUser(any());
        assertThat(result.<String>getClaim("email")).isEqualTo("member@example.com");
        assertThat(result.<Boolean>getClaim("email_verified")).isTrue();
    }

    @Test
    void upsertsKakaoIdentityFromValidatedOidcPrincipal() {
        SocialUserUpsertService upsertService = mock(SocialUserUpsertService.class);
        @SuppressWarnings("unchecked")
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
        OidcUserRequest request = request(KakaoOidcProfileAdapter.ISSUER);
        OidcUser principal = new DefaultOidcUser(List.of(), request.getIdToken(), "sub");
        when(delegate.loadUser(request)).thenReturn(principal);
        when(upsertService.upsert(any())).thenReturn(
                new AuthenticatedUser(17L, "member@example.com", "member", "USER")
        );

        OidcUser result = new CommonOidcUserService(
                upsertService,
                OAuthProviderRegistry.defaults(),
                delegate
        ).loadUser(request);

        ArgumentCaptor<SocialProfile> profile = ArgumentCaptor.forClass(SocialProfile.class);
        verify(upsertService).upsert(profile.capture());
        assertThat(profile.getValue().providerUserId()).isEqualTo("kakao-subject");
        assertThat(profile.getValue().issuer()).isEqualTo(KakaoOidcProfileAdapter.ISSUER);
        assertThat((Object) result.getAttribute(AuthCommonAttributeKeys.APP_USER_ID)).isEqualTo(17L);
        assertThat((Object) result.getAttribute(AuthCommonAttributeKeys.APP_USER_EMAIL))
                .isEqualTo("member@example.com");
        assertThat(result.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void normalizesUnexpectedClaimProcessingFailure() {
        SocialUserUpsertService upsertService = mock(SocialUserUpsertService.class);
        @SuppressWarnings("unchecked")
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
        OidcUserRequest request = request("https://attacker.example");
        when(delegate.loadUser(request)).thenReturn(
                new DefaultOidcUser(List.of(), request.getIdToken(), "sub")
        );

        CommonOidcUserService service = new CommonOidcUserService(
                upsertService,
                OAuthProviderRegistry.defaults(),
                delegate
        );

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("OAUTH_LOGIN_FAILED"));
    }

    private OidcUserRequest request(String issuer) {
        Instant issuedAt = Instant.parse("2026-09-14T00:00:00Z");
        OidcIdToken idToken = new OidcIdToken(
                "id-token-value",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "iss", issuer,
                        "sub", "kakao-subject",
                        "aud", List.of("kakao-client"),
                        "nickname", "Ryan"
                )
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token-value",
                issuedAt,
                issuedAt.plusSeconds(300),
                Set.of("openid", "profile_nickname", "account_email")
        );
        return new OidcUserRequest(clientRegistration(), accessToken, idToken);
    }

    private ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId("kakao-client")
                .clientSecret("not-a-real-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://api.example/login/oauth2/code/kakao")
                .scope("openid", "profile_nickname", "account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .jwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
                .userInfoUri("https://kapi.kakao.com/v1/oidc/userinfo")
                .userNameAttributeName("sub")
                .clientName("Kakao")
                .build();
    }
}
