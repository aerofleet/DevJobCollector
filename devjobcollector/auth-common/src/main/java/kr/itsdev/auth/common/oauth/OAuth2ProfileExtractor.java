package kr.itsdev.auth.common.oauth;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;

public final class OAuth2ProfileExtractor {
    private static final OAuthProviderRegistry PROVIDERS = OAuthProviderRegistry.defaults();

    private OAuth2ProfileExtractor() {
    }

    public static SocialProfile extract(SocialProvider provider, Map<String, Object> attributes) {
        return PROVIDERS.require(provider).extract(attributes);
    }

    public static String defaultNameAttributeKey(SocialProvider provider) {
        return PROVIDERS.require(provider).defaultNameAttributeKey();
    }
}
