package kr.itsdev.devjobcollector.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.security.AuthTokenProperties;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenIssueService implements TokenIssueService {
    private final AuthTokenProperties properties;

    public JwtTokenIssueService(AuthTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public String issueAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getExpiresMinutes(), ChronoUnit.MINUTES);

        return JWT.create()
                .withIssuer(properties.getIssuer())
                .withSubject(String.valueOf(user.id()))
                .withClaim("email", user.email())
                .withClaim("name", user.name())
                .withClaim("role", user.role())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(Algorithm.HMAC256(properties.getSecret()));
    }
}
