package kr.itsdev.devjobcollector.security.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.dto.auth.LoginResponse;
import kr.itsdev.devjobcollector.dto.auth.PersonalSignupRequest;
import kr.itsdev.devjobcollector.dto.auth.PersonalSignupResponse;
import kr.itsdev.devjobcollector.security.account.EmailVerificationToken;
import kr.itsdev.devjobcollector.security.account.EmailVerificationTokenRepository;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserIdentity;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PersonalSignupServiceTest {
    @Mock UserAccountRepository userRepository;
    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock PersonalProfileRepository profileRepository;
    @Mock UserIdentityRepository identityRepository;
    @Mock RequiredConsentService requiredConsentService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock TurnstileVerifier turnstileVerifier;
    @Mock SignupRateLimiter rateLimiter;
    @Mock DisposableEmailPolicy disposableEmailPolicy;
    @Mock TokenIssueService tokenIssueService;

    private AuthSignupProperties properties;
    private PersonalSignupService service;

    @BeforeEach
    void setUp() {
        properties = new AuthSignupProperties();
        properties.setExposeCodeInResponse(true);
        service = new PersonalSignupService(
                userRepository, tokenRepository, profileRepository, identityRepository,
                requiredConsentService,
                passwordEncoder, eventPublisher, turnstileVerifier,
                rateLimiter, disposableEmailPolicy, properties, tokenIssueService);
    }

    @Test
    void signupCreatesPendingAccountAndSendsSixDigitCode() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value.equals("password123") ? "encoded-password" : "hashed-code";
        });
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalSignupResponse response = service.signup(new PersonalSignupRequest(
                " USER@example.com ", "홍길동", "password123", true, true, "turnstile"), "127.0.0.1");

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.status()).isEqualTo("PENDING_EMAIL");
        assertThat(response.developmentVerificationCode()).matches("\\d{6}");
        verify(eventPublisher).publishEvent(any(VerificationCodeIssuedEvent.class));
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(identityRepository).save(any(UserIdentity.class));
        verify(requiredConsentService).validateAccepted(true, true);
        verify(requiredConsentService).recordAccepted(any(UserAccount.class));
    }

    @Test
    void verifyEmailActivatesAccountAndIssuesAccessToken() {
        UserAccount user = UserAccount.pendingLocal("user@example.com", "홍길동", "encoded-password");
        EmailVerificationToken token = new EmailVerificationToken(
                user, "hashed-code", LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserAndUsedAtIsNullOrderByIdDesc(user)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(tokenIssueService.issueAccessToken(any())).thenReturn("issued-token");
        UserIdentity identity = UserIdentity.local(user);
        when(identityRepository.findByUserAndProvider(user, AuthProvider.LOCAL))
                .thenReturn(Optional.of(identity));

        LoginResponse response = service.verifyEmail("user@example.com", "123456");

        assertThat(response.accessToken()).isEqualTo("issued-token");
        assertThat(user.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(identity.getProviderEmailVerified()).isTrue();
        verify(profileRepository).save(any(PersonalProfile.class));
    }

    @Test
    void signupStopsBeforeSideEffectsWhenRequiredConsentIsMissing() {
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED"))
                .when(requiredConsentService).validateAccepted(false, true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.signup(new PersonalSignupRequest(
                        "user@example.com", "홍길동", "password123", false, true, "turnstile"),
                "127.0.0.1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("CONSENT_REQUIRED");

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
        verify(identityRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
