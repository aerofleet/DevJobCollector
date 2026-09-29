package kr.itsdev.devjobcollector.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenVerifier jwtTokenVerifier;
    private final ObjectProvider<UserAccountRepository> userRepositoryProvider;

    public JwtAuthenticationFilter(JwtTokenVerifier jwtTokenVerifier,
                                   ObjectProvider<UserAccountRepository> userRepositoryProvider) {
        this.jwtTokenVerifier = jwtTokenVerifier;
        this.userRepositoryProvider = userRepositoryProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);
        try {
            DecodedJWT jwt = jwtTokenVerifier.verify(token);
            UserAccountRepository userRepository = userRepositoryProvider.getIfAvailable();
            if (userRepository != null) {
                Long userId = Long.valueOf(jwt.getSubject());
                var user = userRepository.findById(userId).orElse(null);
                if (user == null || user.getStatus() != UserAccountStatus.ACTIVE || jwt.getIssuedAt() == null
                        || (user.getSessionRevokedAt() != null && !jwt.getIssuedAt().toInstant().isAfter(
                        user.getSessionRevokedAt().toInstant(ZoneOffset.UTC)))) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Inactive access token");
                    return;
                }
            }
            String role = jwt.getClaim("role").asString();
            List<SimpleGrantedAuthority> authorities = role == null || role.isBlank()
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, authorities);
            authentication.setDetails(jwt.getIssuedAt() == null ? null : jwt.getIssuedAt().toInstant());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JWTVerificationException | NumberFormatException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
        }
    }
}
