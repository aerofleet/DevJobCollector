package kr.itsdev.devjobcollector.controller;

import com.auth0.jwt.interfaces.*;
import java.util.List;
import kr.itsdev.devjobcollector.ai.ResumeAnalysisService;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=ResumeAnalysisController.class,properties="ai.analysis.enabled=true")
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class ResumeAnalysisControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ResumeAnalysisService service;
    @MockitoBean kr.itsdev.devjobcollector.ai.ResumeExplanationService explanations;
    @MockitoBean kr.itsdev.devjobcollector.ai.OpenAiEvidenceClient openai;
    @MockitoBean JwtTokenVerifier verifier;
    @MockitoBean PerfLogProperties perf;
    @BeforeEach void token() {
        var jwt=mock(DecodedJWT.class); var role=mock(Claim.class);
        when(verifier.verify("valid")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(role);
        when(role.asString()).thenReturn("USER");
    }
    @Test void requiresAuthenticationForResultsAndMutations() throws Exception {
        mvc.perform(get("/api/v1/ai/resume-analyses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/resume-analyses/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/ai/resume-analyses/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/resume-analyses/1/explanation")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/resume-analyses/1/explanation").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/resume-analyses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        verifyNoInteractions(explanations);
    }
    @Test void usesAuthenticatedIdentityAndPreventsCaching() throws Exception {
        when(service.list("42")).thenReturn(List.of());
        mvc.perform(get("/api/v1/ai/resume-analyses").header("Authorization","Bearer valid"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).list("42");
        mvc.perform(get("/api/v1/ai/resume-analyses/capabilities").header("Authorization","Bearer valid"))
                .andExpect(jsonPath("$.externalProcessing").value(false));
    }
    @Test void rejectsIncompleteInputBeforeService() throws Exception {
        mvc.perform(post("/api/v1/ai/resume-analyses").header("Authorization","Bearer valid")
                        .header("Idempotency-Key","test-key-12345678").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1,\"consentVersion\":\"local-analysis-v1\",\"careerYears\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
