package kr.itsdev.devjobcollector.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import kr.itsdev.devjobcollector.dto.auth.MemberMeResponse;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MemberControllerSecurityTest {
    @Autowired MockMvc mockMvc;

    @MockitoBean CurrentMemberService currentMemberService;
    @MockitoBean JwtTokenVerifier jwtTokenVerifier;
    @MockitoBean PerfLogProperties perfLogProperties;

    @Test
    void rejectsRequestWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(currentMemberService);
    }

    @Test
    void returnsCurrentMemberForValidBearerToken() throws Exception {
        DecodedJWT jwt = org.mockito.Mockito.mock(DecodedJWT.class);
        Claim roleClaim = org.mockito.Mockito.mock(Claim.class);
        when(jwtTokenVerifier.verify("valid-token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("42");
        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn("USER");
        when(currentMemberService.getCurrentMember("42")).thenReturn(
                new MemberMeResponse(42L, "member@example.com", "에어로플릿", "USER", "ACTIVE")
        );

        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("에어로플릿"))
                .andExpect(jsonPath("$.profileStatus").value("ACTIVE"));

        verify(currentMemberService).getCurrentMember("42");
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        when(jwtTokenVerifier.verify("invalid-token"))
                .thenThrow(new JWTVerificationException("invalid"));

        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(currentMemberService);
    }
}
