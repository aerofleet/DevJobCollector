package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class NaverOAuthConfigurationTest {
    private static final String AUTHORIZATION_URI = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URI = "https://openapi.naver.com/v1/nid/me";

    @Test
    void naverConfigurationIsProfileGatedAndContainsNoCredentialDefault() throws IOException {
        var sources = new YamlPropertySourceLoader()
                .load("application-naver", new ClassPathResource("application-naver.yml"));

        assertThat(property(sources, "spring.config.activate.on-profile")).isEqualTo("naver");
        assertThat(property(sources,
                "spring.security.oauth2.client.registration.naver.client-id"))
                .isEqualTo("${NAVER_CLIENT_ID}");
        assertThat(property(sources,
                "spring.security.oauth2.client.registration.naver.client-secret"))
                .isEqualTo("${NAVER_CLIENT_SECRET}");
        assertThat(property(sources,
                "spring.security.oauth2.client.registration.naver.client-authentication-method"))
                .isEqualTo("client_secret_post");
        assertThat(property(sources,
                "spring.security.oauth2.client.provider.naver.authorization-uri"))
                .isEqualTo(AUTHORIZATION_URI);
        assertThat(property(sources,
                "spring.security.oauth2.client.provider.naver.token-uri"))
                .isEqualTo(TOKEN_URI);
        assertThat(property(sources,
                "spring.security.oauth2.client.provider.naver.user-info-uri"))
                .isEqualTo(USER_INFO_URI);
        assertThat(property(sources,
                "spring.security.oauth2.client.provider.naver.user-name-attribute"))
                .isEqualTo("response");
    }

    @Test
    void authorizationRequestUsesCodeFlowAndUniqueStateWithoutUnneededScope() {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(clientRegistration()),
                "/oauth2/authorization"
        );

        OAuth2AuthorizationRequest first = resolver.resolve(startRequest());
        OAuth2AuthorizationRequest second = resolver.resolve(startRequest());

        assertThat(first).isNotNull();
        assertThat(first.getAuthorizationRequestUri()).startsWith(AUTHORIZATION_URI);
        assertThat(first.getGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(first.getState()).isNotBlank().isNotEqualTo(second.getState());
        assertThat(first.getScopes()).isEmpty();
        assertThat(first.getAdditionalParameters()).doesNotContainKey("nonce");
    }

    private Object property(
            List<org.springframework.core.env.PropertySource<?>> sources,
            String name
    ) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private MockHttpServletRequest startRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/oauth2/authorization/naver");
        request.setServletPath("/oauth2/authorization/naver");
        return request;
    }

    private ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("naver")
                .clientId("naver-client")
                .clientSecret("not-a-real-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri(AUTHORIZATION_URI)
                .tokenUri(TOKEN_URI)
                .userInfoUri(USER_INFO_URI)
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
    }
}
