package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class SessionOAuth2StateRegistryTest {
    private static final MockHttpServletResponse RESPONSE = new MockHttpServletResponse();

    @Test
    void keepsParallelProviderStatesAndConsumesOnlyTheMatchingState() {
        SessionOAuth2StateRegistry registry =
                new SessionOAuth2StateRegistry(Duration.ofMinutes(5), 8);
        MockHttpServletRequest start = new MockHttpServletRequest();
        registry.saveAuthorizationRequest(authorization("google-state"), start, RESPONSE);
        registry.saveAuthorizationRequest(authorization("github-state"), start, RESPONSE);
        MockHttpSession session = (MockHttpSession) start.getSession(false);

        MockHttpServletRequest googleCallback = callback(session, "google-state");
        assertThat(registry.loadAuthorizationRequest(googleCallback).getState())
                .isEqualTo("google-state");
        assertThat(registry.removeAuthorizationRequest(googleCallback, RESPONSE).getState())
                .isEqualTo("google-state");
        assertThat(registry.removeAuthorizationRequest(googleCallback, RESPONSE)).isNull();

        MockHttpServletRequest githubCallback = callback(session, "github-state");
        assertThat(registry.loadAuthorizationRequest(githubCallback).getState())
                .isEqualTo("github-state");
    }

    @Test
    void rejectsUnknownAndExpiredState() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-13T00:00:00Z"));
        SessionOAuth2StateRegistry registry =
                new SessionOAuth2StateRegistry(Duration.ofMinutes(5), 8, clock);
        MockHttpServletRequest start = new MockHttpServletRequest();
        registry.saveAuthorizationRequest(authorization("valid-state"), start, RESPONSE);
        MockHttpSession session = (MockHttpSession) start.getSession(false);

        assertThat(registry.loadAuthorizationRequest(callback(session, "forged-state"))).isNull();
        clock.advance(Duration.ofMinutes(5));
        assertThat(registry.loadAuthorizationRequest(callback(session, "valid-state"))).isNull();
    }

    @Test
    void evictsOldestStateWhenPendingRegistryReachesBound() {
        SessionOAuth2StateRegistry registry =
                new SessionOAuth2StateRegistry(Duration.ofMinutes(5), 2);
        MockHttpServletRequest start = new MockHttpServletRequest();
        registry.saveAuthorizationRequest(authorization("state-1"), start, RESPONSE);
        registry.saveAuthorizationRequest(authorization("state-2"), start, RESPONSE);
        registry.saveAuthorizationRequest(authorization("state-3"), start, RESPONSE);
        MockHttpSession session = (MockHttpSession) start.getSession(false);

        assertThat(registry.loadAuthorizationRequest(callback(session, "state-1"))).isNull();
        assertThat(registry.loadAuthorizationRequest(callback(session, "state-2"))).isNotNull();
        assertThat(registry.loadAuthorizationRequest(callback(session, "state-3"))).isNotNull();
    }

    @Test
    void refusesAuthorizationRequestWithoutState() {
        SessionOAuth2StateRegistry registry =
                new SessionOAuth2StateRegistry(Duration.ofMinutes(5), 8);

        assertThatThrownBy(() -> registry.saveAuthorizationRequest(
                authorization(null),
                new MockHttpServletRequest(),
                RESPONSE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a valid state");
    }

    private OAuth2AuthorizationRequest authorization(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example/authorize")
                .clientId("client")
                .redirectUri("https://api.example/login/oauth2/code/provider")
                .scopes(Set.of("profile"))
                .state(state)
                .build();
    }

    private MockHttpServletRequest callback(MockHttpSession session, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("state", state);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
