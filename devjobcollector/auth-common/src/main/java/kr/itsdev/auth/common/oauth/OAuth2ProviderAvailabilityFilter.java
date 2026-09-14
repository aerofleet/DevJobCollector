package kr.itsdev.auth.common.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public final class OAuth2ProviderAvailabilityFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_PREFIX = "/oauth2/authorization/";

    private final ClientRegistrationRepository clientRegistrationRepository;

    public OAuth2ProviderAvailabilityFilter(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return registrationId(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String registrationId = registrationId(request);
        if (clientRegistrationRepository.findByRegistrationId(registrationId) == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String registrationId(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith(AUTHORIZATION_PREFIX)) {
            return null;
        }
        String value = uri.substring(AUTHORIZATION_PREFIX.length());
        return value.matches("[a-zA-Z0-9_-]+") ? value : null;
    }
}
