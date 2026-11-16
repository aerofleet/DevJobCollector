package kr.itsdev.devjobcollector.jobfile;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.micrometer.core.instrument.MeterRegistry;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.monitoring.RequestTimingInterceptor;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobFilePreviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class JobFilePreviewSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean MeterRegistry metrics;
    @MockitoBean RequestTimingInterceptor timing;
    @MockitoBean JwtTokenVerifier verifier;
    @MockitoBean PerfLogProperties perf;

    @Test void anonymousCannotFetchPreview() throws Exception {
        mvc.perform(get("/api/v1/job-files/123/preview")).andExpect(status().isUnauthorized());
        mvc.perform(head("/api/v1/job-files/123/preview")).andExpect(status().isUnauthorized());
        verifyNoInteractions(jdbc);
    }

    @Test void authenticatedMemberReachesPreviewController() throws Exception {
        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim role = mock(Claim.class);
        when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(role);
        when(role.asString()).thenReturn("USER");
        when(verifier.verify("valid-token")).thenReturn(jwt);
        when(timing.preHandle(any(), any(), any())).thenReturn(true);
        mvc.perform(get("/api/v1/job-files/999/preview").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound());
        verify(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any());
    }
}
