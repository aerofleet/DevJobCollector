package kr.itsdev.devjobcollector.security.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MemberFoundationDomainTest {

    @Test
    void localIdentityUsesNormalizedEmailAsImmutableSubject() {
        UserAccount user = UserAccount.pendingLocal(
                " Local.User@Example.com ", "local", "encoded-password");

        UserIdentity identity = UserIdentity.local(user);

        assertThat(identity.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(identity.getProviderSubject()).isEqualTo("local.user@example.com");
        assertThat(identity.getProviderEmail()).isEqualTo("local.user@example.com");
        assertThat(identity.getProviderEmailVerified()).isFalse();
    }

    @Test
    void socialIdentityPreservesOpaqueSubjectAndRejectsLocalProvider() {
        UserAccount user = UserAccount.activeSocial(
                "social@example.com", "social", AuthProvider.GOOGLE, "legacy-subject");

        UserIdentity identity = UserIdentity.social(
                user, AuthProvider.GOOGLE, "Case-Sensitive-Subject",
                "https://accounts.example.com", "social@example.com", true);

        assertThat(identity.getProviderSubject()).isEqualTo("Case-Sensitive-Subject");
        assertThat(identity.getProviderEmailVerified()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> UserIdentity.social(
                user, AuthProvider.LOCAL, "subject", null, user.getEmail(), true));
    }

    @Test
    void identityDoesNotContainCredentialSecretField() {
        assertThat(Arrays.stream(UserIdentity.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .filter(name -> name.contains("password") || name.contains("secret") || name.contains("token")))
                .isEmpty();
    }

    @Test
    void consentIsCreatedAsIndependentAppendOnlyEvent() {
        UserAccount user = UserAccount.pendingLocal(
                "consent@example.com", "consent", "encoded-password");
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 8, 20, 10, 0);

        UserConsent accepted = UserConsent.accepted(
                user, ConsentType.PRIVACY_POLICY, "2026-08", acceptedAt);
        UserConsent revoked = UserConsent.revoked(
                user, ConsentType.PRIVACY_POLICY, "2026-08", acceptedAt.plusDays(1));

        assertThat(accepted.getAction()).isEqualTo(ConsentAction.ACCEPTED);
        assertThat(revoked.getAction()).isEqualTo(ConsentAction.REVOKED);
        assertThat(Arrays.stream(UserConsent.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .filter(name -> name.startsWith("set") || name.startsWith("update") || name.startsWith("change")))
                .isEmpty();
    }

    @Test
    void personalProfileSupportsContractStatuses() {
        UserAccount user = UserAccount.pendingLocal(
                "profile@example.com", "profile", "encoded-password");
        PersonalProfile profile = PersonalProfile.active(user);

        assertThat(profile.getProfileStatus()).isEqualTo(ProfileStatus.ACTIVE);

        profile.changeStatus(ProfileStatus.PRIVATE);
        assertThat(profile.getProfileStatus()).isEqualTo(ProfileStatus.PRIVATE);

        profile.changeStatus(ProfileStatus.DELETED);
        assertThat(profile.getProfileStatus()).isEqualTo(ProfileStatus.DELETED);
    }
}
