package kr.itsdev.devjobcollector.security.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import(QuerydslConfig.class)
class MemberFoundationRepositoryTest {

    @Autowired UserAccountRepository userRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired UserConsentRepository consentRepository;
    @Autowired PersonalProfileRepository profileRepository;
    @Autowired EntityManager entityManager;

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
    void persistsAndFindsIdentityByStableKeys() {
        UserAccount user = userRepository.save(UserAccount.activeSocial(
                "identity@example.com", "identity", AuthProvider.GOOGLE, "legacy-google-subject"));
        UserIdentity identity = identityRepository.saveAndFlush(UserIdentity.social(
                user, AuthProvider.GOOGLE, "Case-Sensitive-Subject",
                "https://accounts.example.com", user.getEmail(), true));
        entityManager.clear();

        UserIdentity bySubject = identityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE, "Case-Sensitive-Subject").orElseThrow();

        assertThat(bySubject.getId()).isEqualTo(identity.getId());
        assertThat(identityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE, "case-sensitive-subject")).isEmpty();
        assertThat(identityRepository.existsByUserAndProvider(
                bySubject.getUser(), AuthProvider.GOOGLE)).isTrue();
    }

    @Test
    void persistsConsentTimelineAsSeparateRows() {
        UserAccount user = userRepository.save(UserAccount.pendingLocal(
                "timeline@example.com", "timeline", "encoded-password"));
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 8, 20, 10, 0);
        consentRepository.save(UserConsent.accepted(
                user, ConsentType.TERMS_OF_SERVICE, "2026-08", acceptedAt));
        consentRepository.saveAndFlush(UserConsent.revoked(
                user, ConsentType.TERMS_OF_SERVICE, "2026-08", acceptedAt.plusHours(1)));
        entityManager.clear();

        var timeline = consentRepository.findAllByUserAndConsentTypeOrderByOccurredAtAscIdAsc(
                user, ConsentType.TERMS_OF_SERVICE);
        UserConsent latest = consentRepository
                .findFirstByUserAndConsentTypeAndPolicyVersionOrderByOccurredAtDescIdDesc(
                        user, ConsentType.TERMS_OF_SERVICE, "2026-08")
                .orElseThrow();

        assertThat(timeline).extracting(UserConsent::getAction)
                .containsExactly(ConsentAction.ACCEPTED, ConsentAction.REVOKED);
        assertThat(latest.getAction()).isEqualTo(ConsentAction.REVOKED);
    }

    @Test
    void personalProfileUsesUserIdAsPrimaryKey() {
        UserAccount user = userRepository.save(UserAccount.activeSocial(
                "profile@example.com", "profile", AuthProvider.GITHUB, "profile-subject"));
        PersonalProfile profile = profileRepository.saveAndFlush(PersonalProfile.active(user));
        entityManager.clear();

        PersonalProfile found = profileRepository.findById(user.getId()).orElseThrow();

        assertThat(profile.getUserId()).isEqualTo(user.getId());
        assertThat(found.getProfileStatus()).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(profileRepository.existsByUser(found.getUser())).isTrue();
    }

    @Test
    void rejectsDuplicateProviderSubject() {
        UserAccount firstUser = userRepository.save(UserAccount.activeSocial(
                "duplicate-1@example.com", "duplicate-1", AuthProvider.GITHUB, "legacy-1"));
        UserAccount secondUser = userRepository.save(UserAccount.activeSocial(
                "duplicate-2@example.com", "duplicate-2", AuthProvider.GITHUB, "legacy-2"));
        identityRepository.saveAndFlush(UserIdentity.social(
                firstUser, AuthProvider.GITHUB, "duplicate-subject", null,
                firstUser.getEmail(), null));

        assertThatThrownBy(() -> identityRepository.saveAndFlush(UserIdentity.social(
                secondUser, AuthProvider.GITHUB, "duplicate-subject", null,
                secondUser.getEmail(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMultipleIdentitiesForSameUserAndProvider() {
        UserAccount user = userRepository.save(UserAccount.activeSocial(
                "one-provider@example.com", "one-provider", AuthProvider.GOOGLE, "legacy-subject"));
        identityRepository.saveAndFlush(UserIdentity.social(
                user, AuthProvider.GOOGLE, "first-subject", null, user.getEmail(), null));

        assertThatThrownBy(() -> identityRepository.saveAndFlush(UserIdentity.social(
                user, AuthProvider.GOOGLE, "second-subject", null, user.getEmail(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
