package kr.itsdev.auth.common.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import kr.itsdev.auth.common.model.SocialProvider;
import org.junit.jupiter.api.Test;

class OAuthProviderRegistryTest {

    @Test
    void activatesOnlyProvidersWithRegisteredAdapters() {
        OAuthProviderRegistry registry = OAuthProviderRegistry.defaults();

        assertThat(registry.activeProviders())
                .containsExactlyInAnyOrder(
                        SocialProvider.GOOGLE,
                        SocialProvider.GITHUB,
                        SocialProvider.KAKAO,
                        SocialProvider.NAVER
                );
        assertThat(registry.require("google")).isInstanceOf(GoogleOAuth2ProfileAdapter.class);
        assertThat(registry.require("GITHUB")).isInstanceOf(GithubOAuth2ProfileAdapter.class);
        assertThat(registry.require("kakao")).isInstanceOf(KakaoOidcProfileAdapter.class);
        assertThat(registry.require("naver")).isInstanceOf(NaverOAuth2ProfileAdapter.class);
        assertThatThrownBy(() -> registry.require("apple"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported provider: apple");
    }

    @Test
    void rejectsDuplicateProviderAdapters() {
        assertThatThrownBy(() -> new OAuthProviderRegistry(List.of(
                new GoogleOAuth2ProfileAdapter(),
                new GoogleOAuth2ProfileAdapter()
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate OAuth provider adapter");
    }

    @Test
    void mapsAllReservedRegistrationIdsWithoutActivatingThem() {
        assertThat(Set.of(
                SocialProvider.fromRegistrationId("google"),
                SocialProvider.fromRegistrationId("github"),
                SocialProvider.fromRegistrationId("kakao"),
                SocialProvider.fromRegistrationId("naver"),
                SocialProvider.fromRegistrationId("apple")
        )).containsExactlyInAnyOrder(
                SocialProvider.GOOGLE,
                SocialProvider.GITHUB,
                SocialProvider.KAKAO,
                SocialProvider.NAVER,
                SocialProvider.APPLE
        );
    }
}
