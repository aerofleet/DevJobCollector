package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

class KakaoOidcConfigurationTest {
    private static final String ISSUER = "https://kauth.kakao.com";
    private static final String CLIENT_ID = "kakao-client";

    @Test
    void kakaoConfigurationIsProfileGatedAndContainsNoCredentialDefault() throws IOException {
        var sources = new YamlPropertySourceLoader()
                .load("application-kakao", new ClassPathResource("application-kakao.yml"));

        assertThat(property(sources, "spring.config.activate.on-profile")).isEqualTo("kakao");
        assertThat(property(sources,
                "spring.security.oauth2.client.registration.kakao.client-id"))
                .isEqualTo("${KAKAO_CLIENT_ID}");
        assertThat(property(sources,
                "spring.security.oauth2.client.registration.kakao.client-secret"))
                .isEqualTo("${KAKAO_CLIENT_SECRET}");
        assertThat(property(sources,
                "spring.security.oauth2.client.registration.kakao.client-authentication-method"))
                .isEqualTo("client_secret_post");
        assertThat(property(sources,
                "spring.security.oauth2.client.provider.kakao.issuer-uri"))
                .isEqualTo(ISSUER);

        List<String> scopes = List.of(0, 1, 2).stream()
                .map(index -> property(sources,
                        "spring.security.oauth2.client.registration.kakao.scope[" + index + "]"))
                .map(String::valueOf)
                .toList();
        assertThat(scopes).containsExactly("openid", "profile_nickname", "account_email");
    }

    @Test
    void oidcAuthorizationRequestGeneratesUniqueStateAndNonce() {
        var repository = new InMemoryClientRegistrationRepository(clientRegistration());
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                repository,
                "/oauth2/authorization"
        );

        OAuth2AuthorizationRequest first = resolver.resolve(startRequest());
        OAuth2AuthorizationRequest second = resolver.resolve(startRequest());

        assertThat(first).isNotNull();
        assertThat(first.getState()).isNotBlank().isNotEqualTo(second.getState());
        assertThat(first.getAdditionalParameters().get("nonce")).isInstanceOf(String.class);
        assertThat((String) first.getAdditionalParameters().get("nonce")).isNotBlank();
        assertThat(first.getAttributes().get("nonce")).isInstanceOf(String.class);
    }

    @Test
    void oidcValidatorAcceptsExpectedClaimsAndRejectsIssuerAudienceOrExpirationDrift() {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        OidcIdTokenValidator validator = new OidcIdTokenValidator(clientRegistration());
        validator.setClock(Clock.fixed(now, ZoneOffset.UTC));
        validator.setClockSkew(java.time.Duration.ZERO);

        assertThat(validator.validate(jwt(ISSUER, CLIENT_ID, now.plusSeconds(300))).hasErrors())
                .isFalse();
        assertThat(validator.validate(jwt(
                "https://attacker.example", CLIENT_ID, now.plusSeconds(300))).hasErrors())
                .isTrue();
        assertThat(validator.validate(jwt(
                ISSUER, "different-client", now.plusSeconds(300))).hasErrors())
                .isTrue();
        assertThat(validator.validate(jwt(ISSUER, CLIENT_ID, now.minusSeconds(1))).hasErrors())
                .isTrue();
    }

    @Test
    void rs256DecoderAcceptsValidSignatureAndRejectsForgedSignature() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        Instant now = Instant.now();
        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kakao-test-key").build(),
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .audience(CLIENT_ID)
                        .subject("kakao-subject")
                        .issueTime(java.util.Date.from(now.minusSeconds(10)))
                        .expirationTime(java.util.Date.from(now.plusSeconds(300)))
                        .claim("nonce", "nonce-value")
                        .build()
        );
        signedJwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) keyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new OidcIdTokenValidator(clientRegistration()));

        assertThat(decoder.decode(signedJwt.serialize()).getSubject())
                .isEqualTo("kakao-subject");

        String[] parts = signedJwt.serialize().split("\\.");
        parts[2] = Base64URL.encode(new byte[256]).toString();
        String forged = String.join(".", parts);
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
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
                new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        request.setServletPath("/oauth2/authorization/kakao");
        return request;
    }

    private ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId(CLIENT_ID)
                .clientSecret("not-a-real-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile_nickname", "account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .jwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
                .issuerUri(ISSUER)
                .userInfoUri("https://kapi.kakao.com/v1/oidc/userinfo")
                .userNameAttributeName("sub")
                .clientName("Kakao")
                .build();
    }

    private Jwt jwt(String issuer, String audience, Instant expiresAt) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("kakao-subject")
                .audience(List.of(audience))
                .issuedAt(Instant.parse("2026-09-13T23:59:00Z"))
                .expiresAt(expiresAt)
                .claim("nonce", "nonce-value")
                .build();
    }
}
