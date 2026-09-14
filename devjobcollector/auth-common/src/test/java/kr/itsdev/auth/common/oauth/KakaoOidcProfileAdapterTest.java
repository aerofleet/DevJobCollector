package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProvider;
import org.junit.jupiter.api.Test;

class KakaoOidcProfileAdapterTest {
    private final KakaoOidcProfileAdapter adapter = new KakaoOidcProfileAdapter();

    @Test
    void mapsVerifiedOidcClaimsWithoutTreatingEmailAsIdentity() {
        var profile = adapter.extract(Map.of(
                "iss", KakaoOidcProfileAdapter.ISSUER,
                "sub", "42924",
                "email", "User@Example.com",
                "email_verified", true,
                "nickname", "Ryan",
                "picture", "https://example.com/ryan.png"
        ));

        assertThat(profile.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(profile.providerUserId()).isEqualTo("42924");
        assertThat(profile.email()).isEqualTo("User@Example.com");
        assertThat(profile.name()).isEqualTo("Ryan");
        assertThat(profile.profileImageUrl()).isEqualTo("https://example.com/ryan.png");
        assertThat(profile.issuer()).isEqualTo(KakaoOidcProfileAdapter.ISSUER);
        assertThat(profile.emailVerified()).isTrue();
    }

    @Test
    void supportsOptionalKakaoClaims() {
        var profile = adapter.extract(Map.of(
                "iss", KakaoOidcProfileAdapter.ISSUER,
                "sub", "42924"
        ));

        assertThat(profile.providerUserId()).isEqualTo("42924");
        assertThat(profile.email()).isNull();
        assertThat(profile.name()).isNull();
        assertThat(profile.profileImageUrl()).isNull();
    }

    @Test
    void rejectsClaimsFromDifferentIssuer() {
        assertThatThrownBy(() -> adapter.extract(Map.of(
                "iss", "https://attacker.example",
                "sub", "42924"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
    }
}
