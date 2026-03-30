package kr.itsdev.auth.common.oauth;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;

public final class OAuth2ProfileExtractor {
    private OAuth2ProfileExtractor() {
    }

    public static SocialProfile extract(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> new SocialProfile(
                    provider,
                    asString(attributes.get("sub")),
                    asString(attributes.get("email")),
                    asString(attributes.get("name")),
                    asString(attributes.get("picture"))
            );
            case GITHUB -> new SocialProfile(
                    provider,
                    asString(attributes.get("id")),
                    asString(attributes.get("email")),
                    fallback(asString(attributes.get("name")), asString(attributes.get("login"))),
                    asString(attributes.get("avatar_url"))
            );
        };
    }

    public static String defaultNameAttributeKey(SocialProvider provider) {
        return switch (provider) {
            case GOOGLE -> "sub";
            case GITHUB -> "id";
        };
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
