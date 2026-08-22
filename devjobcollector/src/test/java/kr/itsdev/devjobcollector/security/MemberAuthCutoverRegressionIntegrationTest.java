package kr.itsdev.devjobcollector.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import kr.itsdev.auth.common.config.AuthCommonProperties;
import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;
import kr.itsdev.auth.common.oauth.AuthCommonAttributeKeys;
import kr.itsdev.auth.common.oauth.SocialLoginFailureHandler;
import kr.itsdev.auth.common.oauth.SocialLoginSuccessHandler;
import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserIdentity;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import kr.itsdev.devjobcollector.security.service.JpaSocialUserUpsertService;
import kr.itsdev.devjobcollector.security.service.LocalCredentialAuthService;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@EnableConfigurationProperties(AuthLocalLoginProperties.class)
@Import({QuerydslConfig.class, JpaSocialUserUpsertService.class, LocalCredentialAuthService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemberAuthCutoverRegressionIntegrationTest {

    @Autowired JpaSocialUserUpsertService socialLoginService;
    @Autowired LocalCredentialAuthService localLoginService;
    @Autowired UserAccountRepository userRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired PersonalProfileRepository profileRepository;

    @MockitoBean PasswordEncoder passwordEncoder;
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
    void cleanBeforeTest() {
        cleanDatabaseRows();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanDatabaseRows();
    }

    @Test
    void activeLocalIdentityAuthenticatesWithDatabaseCredential() {
        UserAccount account = UserAccount.pendingLocal(
                "local@example.com", "LOCAL user", "encoded-password");
        account.activateEmail();
        account = userRepository.save(account);
        identityRepository.save(UserIdentity.local(account));
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);

        AuthenticatedUser authenticated = localLoginService.authenticate(
                " LOCAL@example.com ", "correct-password");

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.id()).isEqualTo(account.getId());
        assertThat(authenticated.email()).isEqualTo("local@example.com");
    }

    @Test
    void pendingLocalIdentityIsRejectedEvenWithMatchingPassword() {
        UserAccount account = userRepository.save(UserAccount.pendingLocal(
                "pending-local@example.com", "pending", "encoded-password"));
        identityRepository.save(UserIdentity.local(account));
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);

        assertThat(localLoginService.authenticate(
                "pending-local@example.com", "correct-password")).isNull();
    }

    @Test
    void existingGoogleIdentityAuthenticatesByStableSubject() {
        assertExistingSocialIdentityLogin(AuthProvider.GOOGLE, "Google-Subject",
                new SocialProfile(SocialProvider.GOOGLE, "Google-Subject", "google@example.com",
                        "Google user", null, "https://accounts.google.com", true));
    }

    @Test
    void existingGithubIdentityAuthenticatesByStableSubject() {
        assertExistingSocialIdentityLogin(AuthProvider.GITHUB, "424242",
                new SocialProfile(SocialProvider.GITHUB, "424242", "github@example.com",
                        "GitHub user", null, null, null));
    }

    @Test
    void pendingSocialIdentityIsRejectedWithoutRecordingLogin() {
        UserAccount account = userRepository.save(UserAccount.pendingLocal(
                "pending-social@example.com", "pending", "unused-password"));
        UserIdentity identity = identityRepository.save(UserIdentity.social(
                account, AuthProvider.GOOGLE, "pending-subject", "https://accounts.google.com",
                account.getEmail(), true));

        assertThatThrownBy(() -> socialLoginService.upsert(new SocialProfile(
                SocialProvider.GOOGLE, "pending-subject", account.getEmail(), account.getName(),
                null, "https://accounts.google.com", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        assertThat(identityRepository.findById(identity.getId()).orElseThrow().getLastLoginAt()).isNull();
        assertThat(profileRepository.existsByUser(account)).isFalse();
    }

    @Test
    void oauthSuccessCallbackIssuesTokenAndRedirectsWithoutUserDetails() throws Exception {
        AuthCommonProperties properties = callbackProperties();
        when(tokenIssueService.issueAccessToken(any(AuthenticatedUser.class)))
                .thenReturn("signed token/value");
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        AuthCommonAttributeKeys.APP_USER_ID, 17L,
                        AuthCommonAttributeKeys.APP_USER_EMAIL, "oauth@example.com",
                        AuthCommonAttributeKeys.APP_USER_NAME, "OAuth user",
                        AuthCommonAttributeKeys.APP_USER_ROLE, "USER"
                ),
                AuthCommonAttributeKeys.APP_USER_ID
        );
        var response = new MockHttpServletResponse();

        new SocialLoginSuccessHandler(properties, tokenIssueService).onAuthenticationSuccess(
                new MockHttpServletRequest(), response,
                new TestingAuthenticationToken(principal, null));

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "https://frontend.example/oauth/callback?source=oauth&token=signed+token%2Fvalue");
        assertThat(response.getRedirectedUrl()).doesNotContain("oauth@example.com", "OAuth+user");
    }

    @Test
    void oauthFailureCallbackExposesOnlyStableErrorCodes() throws Exception {
        SocialLoginFailureHandler handler = new SocialLoginFailureHandler(callbackProperties());
        var conflictResponse = new MockHttpServletResponse();
        var unexpectedResponse = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), conflictResponse,
                new AccountLinkRequiredException());
        handler.onAuthenticationFailure(new MockHttpServletRequest(), unexpectedResponse,
                new AuthenticationServiceException("provider token=secret-detail"));

        assertThat(conflictResponse.getRedirectedUrl()).isEqualTo(
                "https://frontend.example/oauth/callback?source=oauth&error=ACCOUNT_LINK_REQUIRED");
        assertThat(unexpectedResponse.getRedirectedUrl()).isEqualTo(
                "https://frontend.example/oauth/callback?source=oauth&error=OAUTH_LOGIN_FAILED");
        assertThat(unexpectedResponse.getRedirectedUrl()).doesNotContain("secret-detail");
    }

    private void assertExistingSocialIdentityLogin(
            AuthProvider provider, String subject, SocialProfile profile
    ) {
        UserAccount account = userRepository.save(UserAccount.activeSocial(
                profile.email(), profile.name(), provider, "legacy-subject"));
        UserIdentity identity = identityRepository.save(UserIdentity.social(
                account, provider, subject, profile.issuer(), profile.email(), profile.emailVerified()));

        AuthenticatedUser authenticated = socialLoginService.upsert(profile);

        assertThat(authenticated.id()).isEqualTo(account.getId());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(identityRepository.count()).isEqualTo(1);
        assertThat(identityRepository.findById(identity.getId()).orElseThrow().getLastLoginAt()).isNotNull();
        assertThat(profileRepository.existsByUser(account)).isTrue();
    }

    private AuthCommonProperties callbackProperties() {
        AuthCommonProperties properties = new AuthCommonProperties();
        properties.setFrontendSuccessUri("https://frontend.example/oauth/callback?source=oauth");
        properties.setFrontendFailureUri("https://frontend.example/oauth/callback?source=oauth");
        properties.setTokenQueryParam("token");
        return properties;
    }

    private void cleanDatabaseRows() {
        profileRepository.deleteAllInBatch();
        identityRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
