package kr.itsdev.devjobcollector.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import kr.itsdev.devjobcollector.career.ResumeService;
import kr.itsdev.devjobcollector.career.ResumeStatus;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.dto.career.ResumeDetailResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeStatusUpdateRequest;
import kr.itsdev.devjobcollector.dto.career.ResumeSummaryResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeUpsertRequest;
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

@WebMvcTest(ResumeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ResumeControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ResumeService resumeService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @BeforeEach
    void validToken() {
        DecodedJWT jwt = org.mockito.Mockito.mock(DecodedJWT.class);
        Claim roleClaim = org.mockito.Mockito.mock(Claim.class);
        when(jwtTokenVerifier.verify("valid-token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn("USER");
    }

    @Test
    void rejectsAllResumeOperationsWithoutBearerToken() throws Exception {
        String body = "{\"title\":\"이력서\",\"content\":{}}";
        mockMvc.perform(get("/api/v1/members/me/resumes")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/members/me/resumes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/resumes/11")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/members/me/resumes/11").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/members/me/resumes/11/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/members/me/resumes/11")).andExpect(status().isUnauthorized());
        verifyNoInteractions(resumeService);
    }

    @Test
    void servesAuthenticatedOwnerCrudContract() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 12, 0);
        ResumeDetailResponse detail = new ResumeDetailResponse(
                11L, "이력서", ResumeStatus.DRAFT, objectMapper.readTree("{}"), now, now);
        when(resumeService.list("42"))
                .thenReturn(List.of(new ResumeSummaryResponse(11L, "이력서", ResumeStatus.DRAFT, now, now)));
        when(resumeService.create(eq("42"), any(ResumeUpsertRequest.class))).thenReturn(detail);
        when(resumeService.get("42", 11L)).thenReturn(detail);
        when(resumeService.update(eq("42"), eq(11L), any(ResumeUpsertRequest.class))).thenReturn(detail);
        when(resumeService.changeStatus(eq("42"), eq(11L), any(ResumeStatusUpdateRequest.class)))
                .thenReturn(new ResumeDetailResponse(
                        11L, "이력서", ResumeStatus.READY, objectMapper.readTree("{}"), now, now));
        String body = "{\"title\":\"이력서\",\"content\":{}}";

        mockMvc.perform(get("/api/v1/members/me/resumes").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(11));
        mockMvc.perform(post("/api/v1/members/me/resumes")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/members/me/resumes/11"));
        mockMvc.perform(get("/api/v1/members/me/resumes/11")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("이력서"));
        mockMvc.perform(put("/api/v1/members/me/resumes/11")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/members/me/resumes/11/status")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        mockMvc.perform(delete("/api/v1/members/me/resumes/11")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsInvalidResumePayloads() throws Exception {
        mockMvc.perform(post("/api/v1/members/me/resumes")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"content\":{}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/members/me/resumes")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"이력서\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(resumeService);
    }
}
