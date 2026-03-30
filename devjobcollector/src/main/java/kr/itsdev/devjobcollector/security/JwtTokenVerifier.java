package kr.itsdev.devjobcollector.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenVerifier {
    private final JWTVerifier verifier;

    public JwtTokenVerifier(AuthTokenProperties properties) {
        this.verifier = JWT.require(Algorithm.HMAC256(properties.getSecret()))
                .withIssuer(properties.getIssuer())
                .build();
    }

    public DecodedJWT verify(String token) {
        return verifier.verify(token);
    }
}
