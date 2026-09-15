package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProvider;
import org.junit.jupiter.api.Test;

class NaverOAuth2ProfileAdapterTest {
    private final NaverOAuth2ProfileAdapter adapter = new NaverOAuth2ProfileAdapter();

    @Test
    void extractsNestedNaverProfileUsingApplicationScopedId() {
        var profile = adapter.extract(Map.of(
                "resultcode", "00",
                "message", "success",
                "response", Map.of(
                        "id", "naver-subject",
                        "email", "user@example.com",
                        "name", "Naver User",
                        "nickname", "nickname",
                        "profile_image", "https://example.com/profile.png"
                )
        ));

        assertThat(profile.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(profile.providerUserId()).isEqualTo("naver-subject");
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.name()).isEqualTo("Naver User");
        assertThat(profile.profileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(profile.issuer()).isNull();
        assertThat(profile.emailVerified()).isNull();
        assertThat(adapter.defaultNameAttributeKey()).isEqualTo("response");
    }

    @Test
    void acceptsOptionalProfileClaimsAndFallsBackToNickname() {
        var profile = adapter.extract(Map.of(
                "resultcode", "00",
                "response", Map.of("id", "subject", "nickname", "Naver Nickname")
        ));

        assertThat(profile.email()).isNull();
        assertThat(profile.name()).isEqualTo("Naver Nickname");
        assertThat(profile.profileImageUrl()).isNull();
    }

    @Test
    void rejectsUnsuccessfulResultCode() {
        assertThatThrownBy(() -> adapter.extract(Map.of(
                "resultcode", "024",
                "response", Map.of("id", "subject")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Naver user-info result is not successful");
    }

    @Test
    void rejectsMissingOrMalformedResponseEnvelope() {
        assertThatThrownBy(() -> adapter.extract(Map.of("resultcode", "00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Naver user-info response is required");
        assertThatThrownBy(() -> adapter.extract(Map.of(
                "resultcode", "00",
                "response", "not-an-object"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Naver user-info response is required");
    }

    @Test
    void rejectsMissingApplicationScopedId() {
        assertThatThrownBy(() -> adapter.extract(Map.of(
                "resultcode", "00",
                "response", Map.of("email", "user@example.com")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Naver user-info id is required");
    }
}
