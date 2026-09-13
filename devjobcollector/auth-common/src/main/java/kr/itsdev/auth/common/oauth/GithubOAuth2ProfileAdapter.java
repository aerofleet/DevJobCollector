package kr.itsdev.auth.common.oauth;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;

public final class GithubOAuth2ProfileAdapter implements OAuth2ProfileAdapter {
    @Override
    public SocialProvider provider() {
        return SocialProvider.GITHUB;
    }

    @Override
    public SocialProfile extract(Map<String, Object> attributes) {
        return new SocialProfile(
                provider(),
                OAuth2ProfileAttributes.asString(attributes.get("id")),
                OAuth2ProfileAttributes.asString(attributes.get("email")),
                OAuth2ProfileAttributes.fallback(
                        OAuth2ProfileAttributes.asString(attributes.get("name")),
                        OAuth2ProfileAttributes.asString(attributes.get("login"))
                ),
                OAuth2ProfileAttributes.asString(attributes.get("avatar_url")),
                null,
                null
        );
    }

    @Override
    public String defaultNameAttributeKey() {
        return "id";
    }
}
