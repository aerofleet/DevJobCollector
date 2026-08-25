package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenVerifierTest {
    private static final String ISSUER = "test-issuer";
    private static final String SECRET = "test-secret-that-is-long-enough-1234";

    private JwtTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        AuthTokenProperties properties = new AuthTokenProperties();
        properties.setIssuer(ISSUER);
        properties.setSecret(SECRET);
        verifier = new JwtTokenVerifier(properties);
    }

    @Test
    void acceptsValidToken() {
        String token = token(SECRET, Instant.now().plusSeconds(60));

        assertThat(verifier.verify(token).getSubject()).isEqualTo("42");
    }

    @Test
    void rejectsExpiredToken() {
        String token = token(SECRET, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = token("different-secret-that-is-long-enough", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JWTVerificationException.class);
    }

    private String token(String secret, Instant expiresAt) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject("42")
                .withExpiresAt(Date.from(expiresAt))
                .sign(Algorithm.HMAC256(secret));
    }
}
