package kr.itsdev.devjobcollector.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.itsdev.devjobcollector.career.ApplicationStatus;
import kr.itsdev.devjobcollector.career.JobApplicationService;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.dto.career.JobApplicationCreateRequest;
import kr.itsdev.devjobcollector.dto.career.JobApplicationResponse;
import kr.itsdev.devjobcollector.dto.career.JobApplicationStatusUpdateRequest;
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

@WebMvcTest(JobApplicationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class JobApplicationControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean JobApplicationService applicationService;
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
    void rejectsAllApplicationOperationsWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/applications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/members/me/applications/7")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/members/me/applications/11/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(applicationService);
    }

    @Test
    void servesOwnerListCreateAndStatusChange() throws Exception {
        when(applicationService.list("42")).thenReturn(List.of(response(ApplicationStatus.APPLIED)));
        when(applicationService.create(org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(JobApplicationCreateRequest.class)))
                .thenReturn(response(ApplicationStatus.APPLIED));
        when(applicationService.changeStatus(org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.any(JobApplicationStatusUpdateRequest.class)))
                .thenReturn(response(ApplicationStatus.INTERVIEW));

        mockMvc.perform(get("/api/v1/members/me/applications").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("APPLIED"));
        mockMvc.perform(post("/api/v1/members/me/applications/7")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"서류 제출\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobPostId").value(7));
        mockMvc.perform(patch("/api/v1/members/me/applications/11/status")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERVIEW"));

        verify(applicationService).list("42");
    }

    private JobApplicationResponse response(ApplicationStatus status) {
        return new JobApplicationResponse(11L, 7L, "테스트 기업", "백엔드 개발자", "서울", "신입",
                LocalDate.of(2026, 9, 30), "https://example.com/jobs/7", status,
                LocalDateTime.of(2026, 8, 26, 11, 0), "서류 제출", null);
    }
}
