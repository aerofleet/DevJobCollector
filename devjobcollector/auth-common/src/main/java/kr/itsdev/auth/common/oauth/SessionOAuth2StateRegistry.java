package kr.itsdev.auth.common.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Session-bound OAuth state registry with bounded pending requests, expiry, and one-time removal.
 * Multiple browser tabs can start different provider flows without overwriting each other.
 */
public final class SessionOAuth2StateRegistry
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final String SESSION_ATTRIBUTE =
            SessionOAuth2StateRegistry.class.getName() + ".authorizations";
    private static final int MAX_STATE_LENGTH = 512;

    private final Duration ttl;
    private final int maxPending;
    private final Clock clock;

    public SessionOAuth2StateRegistry(Duration ttl, int maxPending) {
        this(ttl, maxPending, Clock.systemUTC());
    }

    SessionOAuth2StateRegistry(Duration ttl, int maxPending, Clock clock) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("OAuth state TTL must be positive");
        }
        if (maxPending < 1 || maxPending > 100) {
            throw new IllegalArgumentException("OAuth max pending states must be between 1 and 100");
        }
        this.ttl = ttl;
        this.maxPending = maxPending;
        this.clock = clock;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = requestState(request);
        HttpSession session = request.getSession(false);
        if (state == null || session == null) {
            return null;
        }
        synchronized (session) {
            Map<String, StoredAuthorization> states = storedStates(session);
            if (states == null) {
                return null;
            }
            pruneExpired(states);
            StoredAuthorization stored = states.get(state);
            removeSessionAttributeIfEmpty(session, states);
            return stored == null ? null : stored.authorizationRequest();
        }
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String state = validState(authorizationRequest.getState());
        HttpSession session = request.getSession(true);
        synchronized (session) {
            Map<String, StoredAuthorization> states = storedStates(session);
            if (states == null) {
                states = new LinkedHashMap<>();
                session.setAttribute(SESSION_ATTRIBUTE, states);
            }
            pruneExpired(states);
            states.remove(state);
            while (states.size() >= maxPending) {
                Iterator<String> oldest = states.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            states.put(state, new StoredAuthorization(
                    authorizationRequest,
                    clock.instant().plus(ttl)
            ));
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String state = requestState(request);
        HttpSession session = request.getSession(false);
        if (state == null || session == null) {
            return null;
        }
        synchronized (session) {
            Map<String, StoredAuthorization> states = storedStates(session);
            if (states == null) {
                return null;
            }
            pruneExpired(states);
            StoredAuthorization removed = states.remove(state);
            removeSessionAttributeIfEmpty(session, states);
            return removed == null ? null : removed.authorizationRequest();
        }
    }

    private String requestState(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        return state == null || state.isBlank() || state.length() > MAX_STATE_LENGTH ? null : state;
    }

    private String validState(String state) {
        if (state == null || state.isBlank() || state.length() > MAX_STATE_LENGTH) {
            throw new IllegalArgumentException("OAuth authorization request requires a valid state");
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private Map<String, StoredAuthorization> storedStates(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        return value instanceof Map<?, ?> ? (Map<String, StoredAuthorization>) value : null;
    }

    private void pruneExpired(Map<String, StoredAuthorization> states) {
        Instant now = clock.instant();
        states.values().removeIf(stored -> !stored.expiresAt().isAfter(now));
    }

    private void removeSessionAttributeIfEmpty(
            HttpSession session,
            Map<String, StoredAuthorization> states
    ) {
        if (states.isEmpty()) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    private record StoredAuthorization(
            OAuth2AuthorizationRequest authorizationRequest,
            Instant expiresAt
    ) implements Serializable {
    }
}
