package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import kr.itsdev.auth.common.model.SocialProvider;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserIdentity;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, JpaSocialUserUpsertService.class})
class SocialIdentityUpsertIntegrationTest {

    @Autowired JpaSocialUserUpsertService upsertService;
    @Autowired UserAccountRepository userRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired PersonalProfileRepository profileRepository;

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

    @Test
    void existingIdentityWinsWithoutLegacyProviderLookup() {
        UserAccount account = userRepository.save(UserAccount.activeSocial(
                "before@example.com", "before", AuthProvider.GOOGLE, "legacy-subject"));
        UserIdentity identity = identityRepository.save(UserIdentity.social(
                account, AuthProvider.GOOGLE, "Stable-Subject", "https://accounts.google.com",
                "before@example.com", true));

        var authenticated = upsertService.upsert(google(
                "Stable-Subject", "after@example.com", "after", true));

        assertThat(authenticated.id()).isEqualTo(account.getId());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(identity.getProviderSubject()).isEqualTo("Stable-Subject");
        assertThat(identity.getProviderEmail()).isEqualTo("after@example.com");
        assertThat(identity.getProviderEmailVerified()).isTrue();
        assertThat(identity.getLastLoginAt()).isNotNull();
        assertThat(profileRepository.existsByUser(account)).isTrue();
    }

    @Test
    void createsGithubAccountIdentityAndProfileFromStableSubject() {
        var authenticated = upsertService.upsert(github("987654", null, "github-user"));

        UserAccount account = userRepository.findById(authenticated.id()).orElseThrow();
        UserIdentity identity = identityRepository
                .findByProviderAndProviderSubject(AuthProvider.GITHUB, "987654")
                .orElseThrow();

        assertThat(account.getEmail()).isEqualTo("github-987654@social.local");
        assertThat(account.getProviderUserId()).isNull();
        assertThat(identity.getUser().getId()).isEqualTo(account.getId());
        assertThat(identity.getIssuer()).isNull();
        assertThat(identity.getProviderEmailVerified()).isNull();
        assertThat(identity.getLastLoginAt()).isNotNull();
        assertThat(profileRepository.existsByUser(account)).isTrue();
    }

    @Test
    void treatsProviderSubjectAsCaseSensitive() {
        UserAccount existing = userRepository.save(UserAccount.activeSocial(
                "upper@example.com", "upper", AuthProvider.GOOGLE, "Case-Subject"));
        identityRepository.save(UserIdentity.social(
                existing, AuthProvider.GOOGLE, "Case-Subject", "https://accounts.google.com",
                existing.getEmail(), true));

        var authenticated = upsertService.upsert(google(
                "case-subject", "lower@example.com", "lower", true));

        assertThat(authenticated.id()).isNotEqualTo(existing.getId());
        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(identityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE, "Case-Subject")).isPresent();
        assertThat(identityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE, "case-subject")).isPresent();
    }

    @Test
    void rejectsMissingProviderSubjectWithoutWrites() {
        assertThatThrownBy(() -> upsertService.upsert(google(
                " ", "missing@example.com", "missing", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerSubject is required");

        assertThat(userRepository.count()).isZero();
        assertThat(identityRepository.count()).isZero();
        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void rejectsExistingEmailWithoutAutomaticLinkOrStateMutation() {
        for (int index = 0; index < 20; index++) {
            String email = "collision-" + index + "@example.com";
            UserAccount localAccount = userRepository.save(UserAccount.pendingLocal(
                    email, "local-" + index, "encoded-password"));
            localAccount.activateEmail();
            UserIdentity localIdentity = identityRepository.save(UserIdentity.local(localAccount));
            String subject = "new-google-subject-" + index;

            assertThatThrownBy(() -> upsertService.upsert(google(
                    subject, email.toUpperCase(), "google", true)))
                    .isInstanceOf(AccountLinkRequiredException.class)
                    .satisfies(error -> assertThat(((AccountLinkRequiredException) error)
                            .getError().getErrorCode()).isEqualTo("ACCOUNT_LINK_REQUIRED"));

            assertThat(localAccount.getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(localAccount.getProviderUserId()).isNull();
            assertThat(identityRepository.findAllByUserOrderByIdAsc(localAccount))
                    .containsExactly(localIdentity);
            assertThat(identityRepository.findByProviderAndProviderSubject(
                    AuthProvider.GOOGLE, subject)).isEmpty();
            assertThat(profileRepository.existsByUser(localAccount)).isFalse();
        }

        assertThat(userRepository.count()).isEqualTo(20);
        assertThat(identityRepository.count()).isEqualTo(20);
        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void rejectsNonActiveIdentityOwnerWithoutStateMutation() {
        UserAccount pendingAccount = userRepository.save(UserAccount.pendingLocal(
                "pending@example.com", "pending", "encoded-password"));
        UserIdentity googleIdentity = identityRepository.save(UserIdentity.social(
                pendingAccount, AuthProvider.GOOGLE, "pending-google-subject",
                "https://accounts.google.com", "pending@example.com", true));

        assertThatThrownBy(() -> upsertService.upsert(google(
                "pending-google-subject", "pending@example.com", "pending", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("INVALID_CREDENTIALS");

        assertThat(pendingAccount.getStatus().name()).isEqualTo("PENDING_EMAIL");
        assertThat(googleIdentity.getLastLoginAt()).isNull();
        assertThat(profileRepository.existsByUser(pendingAccount)).isFalse();
    }

    private SocialProfile google(String subject, String email, String name, Boolean emailVerified) {
        return new SocialProfile(
                SocialProvider.GOOGLE,
                subject,
                email,
                name,
                null,
                "https://accounts.google.com",
                emailVerified
        );
    }

    private SocialProfile github(String subject, String email, String name) {
        return new SocialProfile(
                SocialProvider.GITHUB,
                subject,
                email,
                name,
                null,
                null,
                null
        );
    }
}
