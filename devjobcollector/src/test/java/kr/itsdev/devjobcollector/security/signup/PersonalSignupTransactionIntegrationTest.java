package kr.itsdev.devjobcollector.security.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.dto.auth.PersonalSignupRequest;
import kr.itsdev.devjobcollector.security.account.ConsentAction;
import kr.itsdev.devjobcollector.security.account.ConsentType;
import kr.itsdev.devjobcollector.security.account.EmailVerificationTokenRepository;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserConsentRepository;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "auth.signup.expose-code-in-response=true",
        "auth.signup.terms-policy-version=terms-test-v1",
        "auth.signup.privacy-policy-version=privacy-test-v1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@EnableConfigurationProperties(AuthSignupProperties.class)
@Import({QuerydslConfig.class, PersonalSignupService.class, RequiredConsentService.class,
        VerificationMailEventListener.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PersonalSignupTransactionIntegrationTest {

    @Autowired PersonalSignupService signupService;
    @Autowired AuthSignupProperties properties;
    @Autowired UserAccountRepository userRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired UserConsentRepository consentRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @Autowired PersonalProfileRepository profileRepository;

    @MockitoBean PasswordEncoder passwordEncoder;
    @MockitoBean VerificationMailService mailService;
    @MockitoBean TurnstileVerifier turnstileVerifier;
    @MockitoBean SignupRateLimiter rateLimiter;
    @MockitoBean DisposableEmailPolicy disposableEmailPolicy;
    @MockitoBean TokenIssueService tokenIssueService;

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @BeforeEach
    void configureCollaborators() {
        cleanDatabaseRows();
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> "encoded:" + invocation.getArgument(0, String.class));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1, String.class)
                        .equals("encoded:" + invocation.getArgument(0, String.class)));
        when(tokenIssueService.issueAccessToken(org.mockito.ArgumentMatchers.any()))
                .thenReturn("issued-token");
    }

    @AfterEach
    void cleanAfterTest() {
        properties.setTermsPolicyVersion("terms-test-v1");
        properties.setPrivacyPolicyVersion("privacy-test-v1");
        cleanDatabaseRows();
    }

    @Test
    void signupAtomicallyPersistsLocalIdentityRequiredConsentsAndToken() {
        var signup = signupService.signup(request("atomic@example.com", true, true), "127.0.0.1");

        UserAccount user = userRepository.findByEmailIgnoreCase("atomic@example.com").orElseThrow();
        var consents = consentRepository.findAll();

        assertThat(identityRepository.findAllByUserOrderByIdAsc(user))
                .singleElement()
                .satisfies(identity -> {
                    assertThat(identity.getProviderSubject()).isEqualTo("atomic@example.com");
                    assertThat(identity.getProviderEmailVerified()).isFalse();
                });
        assertThat(consents).hasSize(2)
                .extracting(consent -> consent.getConsentType())
                .containsExactlyInAnyOrder(ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_POLICY);
        assertThat(consents).extracting(consent -> consent.getAction())
                .containsOnly(ConsentAction.ACCEPTED);
        assertThat(consents).extracting(consent -> consent.getPolicyVersion())
                .containsExactlyInAnyOrder("terms-test-v1", "privacy-test-v1");
        assertThat(consents).extracting(consent -> consent.getOccurredAt())
                .containsOnly(consents.getFirst().getOccurredAt());
        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThat(profileRepository.count()).isZero();
        verify(mailService).sendCode("atomic@example.com", signup.developmentVerificationCode());
    }

    @Test
    void verifyEmailCreatesProfileAndMarksLocalIdentityVerified() {
        var signup = signupService.signup(request("verify@example.com", true, true), "127.0.0.1");

        signupService.verifyEmail("verify@example.com", signup.developmentVerificationCode());

        UserAccount user = userRepository.findByEmailIgnoreCase("verify@example.com").orElseThrow();
        assertThat(profileRepository.existsByUser(user)).isTrue();
        assertThat(identityRepository.findAllByUserOrderByIdAsc(user))
                .singleElement()
                .satisfies(identity -> assertThat(identity.getProviderEmailVerified()).isTrue());
    }

    @Test
    void failedVerificationAttemptsPersistAndBlockEvenTheCorrectCode() {
        var signup = signupService.signup(request("attempts@example.com", true, true), "127.0.0.1");
        String wrong = signup.developmentVerificationCode().equals("000000") ? "000001" : "000000";
        for (int i = 0; i < properties.getMaxVerificationAttempts(); i++) {
            assertThatThrownBy(() -> signupService.verifyEmail("attempts@example.com", wrong))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
        UserAccount user = userRepository.findByEmailIgnoreCase("attempts@example.com").orElseThrow();
        assertThat(tokenRepository.findFirstByUserAndUsedAtIsNullOrderByIdDesc(user).orElseThrow().getAttempts())
                .isEqualTo(properties.getMaxVerificationAttempts());
        assertThatThrownBy(() -> signupService.verifyEmail("attempts@example.com", signup.developmentVerificationCode()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void suspendedAccountCannotBeReactivatedByEmailCodeOrResend() {
        var signup = signupService.signup(request("suspended@example.com", true, true), "127.0.0.1");
        UserAccount user = userRepository.findByEmailIgnoreCase("suspended@example.com").orElseThrow();
        user.moderate(kr.itsdev.devjobcollector.security.account.UserAccountStatus.SUSPENDED,
                java.time.LocalDateTime.now());
        userRepository.saveAndFlush(user);
        assertThatThrownBy(() -> signupService.verifyEmail("suspended@example.com", signup.developmentVerificationCode()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> signupService.resend("suspended@example.com", "turnstile", "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(userRepository.findByEmailIgnoreCase("suspended@example.com").orElseThrow().getStatus())
                .isEqualTo(kr.itsdev.devjobcollector.security.account.UserAccountStatus.SUSPENDED);
    }

    @Test
    void concurrentInvalidCodesDoNotLoseAttempts() throws Exception {
        var signup = signupService.signup(request("concurrent@example.com", true, true), "127.0.0.1");
        String wrong = signup.developmentVerificationCode().equals("000000") ? "000001" : "000000";
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    assertThatThrownBy(() -> signupService.verifyEmail("concurrent@example.com", wrong))
                            .isInstanceOf(InvalidVerificationCodeException.class);
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        UserAccount user = userRepository.findByEmailIgnoreCase("concurrent@example.com").orElseThrow();
        assertThat(tokenRepository.findFirstByUserAndUsedAtIsNullOrderByIdDesc(user).orElseThrow().getAttempts())
                .isEqualTo(4);
    }

    @Test
    void rejectsMissingRequiredConsentWithoutWrites() {
        assertThatThrownBy(() -> signupService.signup(
                request("missing-consent@example.com", false, true), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException responseError = (ResponseStatusException) error;
                    assertThat(responseError.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(responseError.getReason()).isEqualTo("CONSENT_REQUIRED");
                });

        assertAllSignupTablesEmpty();
    }

    @Test
    void duplicateEmailDoesNotAppendAdditionalConsentOrToken() {
        signupService.signup(request("duplicate@example.com", true, true), "127.0.0.1");

        assertThatThrownBy(() -> signupService.signup(
                request(" DUPLICATE@example.com ", true, true), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(identityRepository.count()).isEqualTo(1);
        assertThat(consentRepository.count()).isEqualTo(2);
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidServerPolicyVersionRollsBackWholeSignupTransaction() {
        properties.setTermsPolicyVersion(" ");

        assertThatThrownBy(() -> signupService.signup(
                request("rollback@example.com", true, true), "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyVersion is required");

        assertAllSignupTablesEmpty();
    }

    @Test
    void mailFailureAfterCommitDoesNotRollbackSignupData() {
        doThrow(new IllegalStateException("mail unavailable"))
                .when(mailService).sendCode(anyString(), anyString());

        signupService.signup(request("mail-failure@example.com", true, true), "127.0.0.1");

        verify(mailService).sendCode(org.mockito.ArgumentMatchers.eq("mail-failure@example.com"), anyString());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(identityRepository.count()).isEqualTo(1);
        assertThat(consentRepository.count()).isEqualTo(2);
        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThat(profileRepository.count()).isZero();
    }

    private PersonalSignupRequest request(String email, boolean terms, boolean privacy) {
        return new PersonalSignupRequest(
                email, "테스트 사용자", "password123", terms, privacy, "turnstile");
    }

    private void assertAllSignupTablesEmpty() {
        assertThat(userRepository.count()).isZero();
        assertThat(identityRepository.count()).isZero();
        assertThat(consentRepository.count()).isZero();
        assertThat(tokenRepository.count()).isZero();
        assertThat(profileRepository.count()).isZero();
    }

    private void cleanDatabaseRows() {
        profileRepository.deleteAllInBatch();
        tokenRepository.deleteAllInBatch();
        consentRepository.deleteAllInBatch();
        identityRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
