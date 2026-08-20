package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProvider;
import kr.itsdev.auth.common.oauth.OAuth2ProfileExtractor;
import org.junit.jupiter.api.Test;

class OAuth2ProfileExtractorTest {

    @Test
    void extractsGoogleStableSubjectIssuerAndEmailVerificationEvidence() {
        var profile = OAuth2ProfileExtractor.extract(SocialProvider.GOOGLE, Map.of(
                "sub", "Google-Subject",
                "email", "User@Example.com",
                "email_verified", true,
                "name", "Google User",
                "picture", "https://example.com/picture"
        ));

        assertThat(profile.providerUserId()).isEqualTo("Google-Subject");
        assertThat(profile.issuer()).isEqualTo("https://accounts.google.com");
        assertThat(profile.emailVerified()).isTrue();
    }

    @Test
    void keepsGithubVerificationEvidenceUnknown() {
        var profile = OAuth2ProfileExtractor.extract(SocialProvider.GITHUB, Map.of(
                "id", 12345,
                "login", "github-user",
                "email", "github@example.com"
        ));

        assertThat(profile.providerUserId()).isEqualTo("12345");
        assertThat(profile.name()).isEqualTo("github-user");
        assertThat(profile.issuer()).isNull();
        assertThat(profile.emailVerified()).isNull();
    }
}
