package kr.itsdev.devjobcollector.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.List;
import kr.itsdev.devjobcollector.ai.ResumePiiGate;
import kr.itsdev.devjobcollector.ai.ResumePreprocessingService;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ResumePreprocessingController.class, properties = "ai.preprocessing.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ResumePreprocessingControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ResumePreprocessingService preprocessing;
    @MockitoBean JwtTokenVerifier verifier;
    @MockitoBean PerfLogProperties perf;

    @BeforeEach
    void token() {
        var jwt = mock(DecodedJWT.class);
        var role = mock(Claim.class);
        when(verifier.verify("valid")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(role);
        when(role.asString()).thenReturn("USER");
    }

    @Test
    void deniesUnauthenticatedJsonAndMultipartRequests() throws Exception {
        mvc.perform(post("/api/v1/ai/resume-previews").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":1,\"localProcessingConsent\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/ai/resume-previews").file("file", new byte[]{1})
                .param("resumeId", "1").param("localProcessingConsent", "true")).andExpect(status().isUnauthorized());
        verifyNoInteractions(preprocessing);
    }

    @Test
    void returnsNonCacheableBlockedPreviewForAuthenticatedMember() throws Exception {
        when(preprocessing.preview("42", 1L, true, null)).thenReturn(new ResumePiiGate().mask("Java", List.of()));
        mvc.perform(post("/api/v1/ai/resume-previews").header("Authorization", "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"resumeId\":1,\"localProcessingConsent\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.piiGatePassed").value(false))
                .andExpect(jsonPath("$.blockReason").value("PII_COVERAGE_UNVERIFIED"));
    }

    @Test
    void rejectsMissingConsentAndInvalidIdentifiers() throws Exception {
        for (String body : List.of("{}", "{\"resumeId\":1}", "{\"resumeId\":-1,\"localProcessingConsent\":true}")) {
            mvc.perform(post("/api/v1/ai/resume-previews").header("Authorization", "Bearer valid")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(preprocessing);
    }
}
