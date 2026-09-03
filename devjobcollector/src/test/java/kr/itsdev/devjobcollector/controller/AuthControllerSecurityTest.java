package kr.itsdev.devjobcollector.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Date;
import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import kr.itsdev.devjobcollector.dto.auth.AccountLinkStartResponse;
import kr.itsdev.devjobcollector.security.JwtAuthenticationFilter;
import kr.itsdev.devjobcollector.security.JwtTokenVerifier;
import kr.itsdev.devjobcollector.security.SecurityConfig;
import kr.itsdev.devjobcollector.security.service.AccountLinkService;
import kr.itsdev.devjobcollector.security.service.LocalCredentialAuthService;
import kr.itsdev.devjobcollector.security.signup.PersonalSignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerSecurityTest {
    @Autowired MockMvc mockMvc;

    @MockitoBean LocalCredentialAuthService localCredentialAuthService;
    @MockitoBean TokenIssueService tokenIssueService;
    @MockitoBean PersonalSignupService personalSignupService;
    @MockitoBean AccountLinkService accountLinkService;
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
        when(jwt.getIssuedAt()).thenReturn(Date.from(Instant.now()));
    }

    @Test
    void rejectsAccountLinkStartWithoutExistingAccountAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/account-links/google/start"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountLinkService);
    }

    @Test
    void startsAccountLinkForAuthenticatedExistingAccount() throws Exception {
        when(accountLinkService.start(eq("42"), eq("google"), any(Instant.class), any(HttpServletRequest.class)))
                .thenReturn(new AccountLinkStartResponse("/oauth2/authorization/google", 300));

        mockMvc.perform(post("/api/v1/auth/account-links/google/start")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationPath").value("/oauth2/authorization/google"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));

        verify(accountLinkService).start(eq("42"), eq("google"), any(Instant.class), any(HttpServletRequest.class));
    }
}
