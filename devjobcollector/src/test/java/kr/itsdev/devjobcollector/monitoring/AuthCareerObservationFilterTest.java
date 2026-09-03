package kr.itsdev.devjobcollector.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCareerObservationFilterTest {

    @ParameterizedTest
    @MethodSource("observedPaths")
    void classifiesOnlyAuthenticationAndCareerPaths(
            String path,
            AuthCareerObservationFilter.ObservationScope expected
    ) {
        assertThat(AuthCareerObservationFilter.scope(path)).isEqualTo(expected);
    }

    @Test
    void ignoresUnrelatedPaths() {
        assertThat(AuthCareerObservationFilter.scope("/api/v1/jobs/search")).isNull();
        assertThat(AuthCareerObservationFilter.scope("/api/v1/members/metadata")).isNull();
        assertThat(AuthCareerObservationFilter.scope(null)).isNull();
    }

    @Test
    void preservesResponseStatusFromTheRemainingFilterChain() throws Exception {
        AuthCareerObservationFilter filter = new AuthCareerObservationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, chainResponse) ->
                ((MockHttpServletResponse) chainResponse).setStatus(401));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static Stream<Arguments> observedPaths() {
        return Stream.of(
                Arguments.of("/api/v1/auth/login", AuthCareerObservationFilter.ObservationScope.AUTH),
                Arguments.of("/api/v1/auth/account-links/google", AuthCareerObservationFilter.ObservationScope.AUTH),
                Arguments.of("/oauth2/authorization/google", AuthCareerObservationFilter.ObservationScope.AUTH),
                Arguments.of("/login/oauth2/code/github", AuthCareerObservationFilter.ObservationScope.AUTH),
                Arguments.of("/api/v1/members/me", AuthCareerObservationFilter.ObservationScope.AUTH),
                Arguments.of("/api/v1/members/me/bookmarks", AuthCareerObservationFilter.ObservationScope.CAREER),
                Arguments.of("/api/v1/members/me/recent-jobs/1", AuthCareerObservationFilter.ObservationScope.CAREER),
                Arguments.of("/api/v1/members/me/applications", AuthCareerObservationFilter.ObservationScope.CAREER),
                Arguments.of("/api/v1/members/me/resumes/2", AuthCareerObservationFilter.ObservationScope.CAREER)
        );
    }
}
