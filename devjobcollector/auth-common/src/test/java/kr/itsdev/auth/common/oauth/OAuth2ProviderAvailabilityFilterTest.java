package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class OAuth2ProviderAvailabilityFilterTest {

    @Test
    void returnsNotFoundWhenProviderIsNotRegistered() throws Exception {
        ClientRegistrationRepository registrations = mock(ClientRegistrationRepository.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("/oauth2/authorization/kakao");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2ProviderAvailabilityFilter(registrations).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(chain);
    }

    @Test
    void delegatesWhenProviderIsRegistered() throws Exception {
        ClientRegistrationRepository registrations = mock(ClientRegistrationRepository.class);
        ClientRegistration google = mock(ClientRegistration.class);
        when(registrations.findByRegistrationId("google")).thenReturn(google);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("/oauth2/authorization/google");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2ProviderAvailabilityFilter(registrations).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresUnrelatedPaths() throws Exception {
        ClientRegistrationRepository registrations = mock(ClientRegistrationRepository.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2ProviderAvailabilityFilter(registrations).doFilter(request, response, chain);

        verifyNoInteractions(registrations);
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }
}
