package kr.itsdev.devjobcollector.controller;

import com.auth0.jwt.interfaces.*;
import kr.itsdev.devjobcollector.ai.ResumeContentReviewService;
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

@WebMvcTest(value=ResumeContentReviewController.class,properties="ai.analysis.enabled=true")
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class ResumeContentReviewControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ResumeContentReviewService service;
    @MockitoBean JwtTokenVerifier verifier;
    @MockitoBean PerfLogProperties perf;
    @BeforeEach void token() {
        var jwt=mock(DecodedJWT.class); var role=mock(Claim.class);
        when(verifier.verify("valid")).thenReturn(jwt); when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(role); when(role.asString()).thenReturn("USER");
    }
    @Test void protectsPreviewAndResultsAndTransmission() throws Exception {
        mvc.perform(put("/api/v1/ai/resume-analyses/1/content-review/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/resume-analyses/1/content-review/preview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/resume-analyses/1/content-review")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/resume-analyses/1/content-review").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void confirmationUsesOwnerAndValidatesOption() throws Exception {
        mvc.perform(put("/api/v1/ai/resume-analyses/1/content-review/confirm").header("Authorization","Bearer valid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"findingId\":\"rewrite\",\"option\":1}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).confirm("42",1L,"rewrite",1);
        clearInvocations(service);
        mvc.perform(put("/api/v1/ai/resume-analyses/1/content-review/confirm").header("Authorization","Bearer valid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"findingId\":\"rewrite\",\"option\":9}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void usesAuthenticatedOwnerAndDoesNotCachePreview() throws Exception {
        mvc.perform(post("/api/v1/ai/resume-analyses/1/content-review/preview").header("Authorization","Bearer valid"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).preview("42",1L);
        clearInvocations(service);
        mvc.perform(post("/api/v1/ai/resume-analyses/1/content-review").header("Authorization","Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"consentVersion\":\"yes\",\"previewHash\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
