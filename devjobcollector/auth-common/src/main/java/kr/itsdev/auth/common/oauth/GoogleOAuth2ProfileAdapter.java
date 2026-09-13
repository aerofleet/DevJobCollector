package kr.itsdev.auth.common.oauth;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;

public final class GoogleOAuth2ProfileAdapter implements OAuth2ProfileAdapter {
    static final String ISSUER = "https://accounts.google.com";

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public SocialProfile extract(Map<String, Object> attributes) {
        return new SocialProfile(
                provider(),
                OAuth2ProfileAttributes.asString(attributes.get("sub")),
                OAuth2ProfileAttributes.asString(attributes.get("email")),
                OAuth2ProfileAttributes.asString(attributes.get("name")),
                OAuth2ProfileAttributes.asString(attributes.get("picture")),
                ISSUER,
                OAuth2ProfileAttributes.firstBoolean(attributes, "email_verified", "verified_email")
        );
    }

    @Override
    public String defaultNameAttributeKey() {
        return "sub";
    }
}
