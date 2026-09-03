package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserIdentity;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.util.ReflectionTestUtils;

class AccountLinkServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    private UserAccountRepository userRepository;
    private UserIdentityRepository identityRepository;
    private Clock clock;
    private AccountLinkService service;
    private MockHttpServletRequest request;
    private UserAccount target;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserAccountRepository.class);
        identityRepository = mock(UserIdentityRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccountLinkService(userRepository, identityRepository, clock);
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        target = UserAccount.activeSocial(
                "member@example.com", "member", AuthProvider.GITHUB, null);
        ReflectionTestUtils.setField(target, "id", 17L);

        when(userRepository.findById(17L)).thenReturn(Optional.of(target));
        when(identityRepository.existsByUserAndProvider(target, AuthProvider.GOOGLE)).thenReturn(false);
        when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-17"))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void linksVerifiedSameEmailProviderAfterAuthenticatedIntent() {
        var start = service.start("17", "google", NOW, request);

        var linked = service.linkIfRequested(google("member@example.com", true));

        assertThat(start.authorizationPath()).isEqualTo("/oauth2/authorization/google");
        assertThat(start.expiresInSeconds()).isEqualTo(300);
        assertThat(linked).isPresent();
        assertThat(linked.orElseThrow().id()).isEqualTo(17L);

        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(identityRepository).save(identityCaptor.capture());
        UserIdentity identity = identityCaptor.getValue();
        assertThat(identity.getUser()).isSameAs(target);
        assertThat(identity.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(identity.getProviderSubject()).isEqualTo("google-17");
        assertThat(identity.getProviderEmail()).isEqualTo("member@example.com");
        assertThat(identity.getProviderEmailVerified()).isTrue();
        assertThat(identity.getLastLoginAt()).isNotNull();
    }

    @Test
    void rejectsDifferentEmailWithoutWritingIdentity() {
        service.start("17", "google", NOW, request);

        assertOAuthError(
                () -> service.linkIfRequested(google("attacker@example.com", true)),
                AccountLinkService.LINK_CONFLICT
        );
        verify(identityRepository, never()).save(any());
    }

    @Test
    void rejectsUnverifiedProviderEmailWithoutWritingIdentity() {
        service.start("17", "google", NOW, request);

        assertOAuthError(
                () -> service.linkIfRequested(google("member@example.com", false)),
                AccountLinkService.LINK_CONFLICT
        );
        verify(identityRepository, never()).save(any());
    }

    @Test
    void rejectsProviderMismatchAndConsumesIntent() {
        service.start("17", "google", NOW, request);

        assertOAuthError(
                () -> service.linkIfRequested(github("member@example.com")),
                AccountLinkService.LINK_INVALID
        );
        assertThat(service.linkIfRequested(google("member@example.com", true))).isEmpty();
        verify(identityRepository, never()).save(any());
    }

    @Test
    void rejectsSubjectAlreadyOwnedByAnotherAccount() {
        service.start("17", "google", NOW, request);
        UserAccount other = UserAccount.activeSocial(
                "other@example.com", "other", AuthProvider.GOOGLE, null);
        when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-17"))
                .thenReturn(Optional.of(UserIdentity.social(
                        other, AuthProvider.GOOGLE, "google-17", "https://accounts.google.com",
                        "other@example.com", true)));

        assertOAuthError(
                () -> service.linkIfRequested(google("member@example.com", true)),
                AccountLinkService.LINK_CONFLICT
        );
        verify(identityRepository, never()).save(any());
    }

    @Test
    void rejectsExpiredIntent() {
        service.start("17", "google", NOW, request);
        service = new AccountLinkService(
                userRepository,
                identityRepository,
                Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC)
        );

        assertOAuthError(
                () -> service.linkIfRequested(google("member@example.com", true)),
                AccountLinkService.LINK_INVALID
        );
        verify(identityRepository, never()).save(any());
    }

    @Test
    void rejectsAccountLinkStartWithoutRecentReauthentication() {
        assertThatThrownBy(() -> service.start(
                "17", "google", NOW.minusSeconds(301), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getReason())
                        .isEqualTo("ACCOUNT_REAUTH_REQUIRED"));

        assertThat(request.getSession(false)).isNull();
        verify(identityRepository, never()).existsByUserAndProvider(any(), any());
    }

    private void assertOAuthError(Runnable action, String expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(error -> assertThat(((OAuth2AuthenticationException) error)
                        .getError().getErrorCode()).isEqualTo(expectedCode));
    }

    private SocialProfile google(String email, boolean verified) {
        return new SocialProfile(
                SocialProvider.GOOGLE,
                "google-17",
                email,
                "Google member",
                null,
                "https://accounts.google.com",
                verified
        );
    }

    private SocialProfile github(String email) {
        return new SocialProfile(
                SocialProvider.GITHUB,
                "github-17",
                email,
                "GitHub member",
                null,
                null,
                true
        );
    }
}
