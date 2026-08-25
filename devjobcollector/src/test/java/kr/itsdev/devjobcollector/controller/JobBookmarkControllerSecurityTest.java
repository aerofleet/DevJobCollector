package kr.itsdev.devjobcollector.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.LocalDate;
import java.util.List;
import kr.itsdev.devjobcollector.career.JobBookmarkService;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.dto.career.JobBookmarkResponse;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobBookmarkController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class JobBookmarkControllerSecurityTest {
    @Autowired MockMvc mockMvc;

    @MockitoBean JobBookmarkService bookmarkService;
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
    void rejectsAllBookmarkOperationsWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/bookmarks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/members/me/bookmarks/7")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/members/me/bookmarks/7")).andExpect(status().isUnauthorized());

        verifyNoInteractions(bookmarkService);
    }

    @Test
    void returnsAuthenticatedOwnersBookmarkList() throws Exception {
        when(bookmarkService.list("42")).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/members/me/bookmarks")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobPostId").value(7))
                .andExpect(jsonPath("$[0].companyName").value("테스트 기업"));
    }

    @Test
    void createsAndDeletesWithAuthenticatedSubject() throws Exception {
        when(bookmarkService.create("42", 7L)).thenReturn(response());

        mockMvc.perform(post("/api/v1/members/me/bookmarks/7")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPostId").value(7));
        mockMvc.perform(delete("/api/v1/members/me/bookmarks/7")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());

        verify(bookmarkService).create("42", 7L);
        verify(bookmarkService).delete("42", 7L);
    }

    private JobBookmarkResponse response() {
        return new JobBookmarkResponse(
                11L, 7L, "테스트 기업", "백엔드 개발자", "서울", "신입",
                LocalDate.of(2026, 9, 30), "https://example.com/jobs/7", null
        );
    }
}
