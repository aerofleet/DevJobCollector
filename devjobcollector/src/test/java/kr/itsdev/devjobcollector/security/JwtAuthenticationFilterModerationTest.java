package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterModerationTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserAccountRepository> provider = mock(ObjectProvider.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final JwtTokenVerifier verifier = mock(JwtTokenVerifier.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, provider);

    @Test
    void rejectsTokenForSuspendedUser() throws Exception {
        UserAccount user = UserAccount.activeSocial("member@example.com", "Member", AuthProvider.GITHUB, "id");
        user.moderate(UserAccountStatus.SUSPENDED, LocalDateTime.now(ZoneOffset.UTC));
        assertRejected(user, Instant.now().minusSeconds(30));
    }

    @Test
    void rejectsOldTokenAfterReactivation() throws Exception {
        UserAccount user = UserAccount.activeSocial("member@example.com", "Member", AuthProvider.GITHUB, "id");
        Instant oldIssue = Instant.now().minusSeconds(60);
        user.moderate(UserAccountStatus.SUSPENDED, LocalDateTime.now(ZoneOffset.UTC).minusSeconds(20));
        user.moderate(UserAccountStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        assertRejected(user, oldIssue);
    }

    private void assertRejected(UserAccount user, Instant issuedAt) throws Exception {
        when(provider.getIfAvailable()).thenReturn(users);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn("7");
        when(jwt.getIssuedAt()).thenReturn(Date.from(issuedAt));
        when(verifier.verify("member-token")).thenReturn(jwt);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        request.addHeader("Authorization", "Bearer member-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }
}
