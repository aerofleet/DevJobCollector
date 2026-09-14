package kr.itsdev.auth.common.oauth;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;

public final class KakaoOidcProfileAdapter implements OAuth2ProfileAdapter {
    public static final String ISSUER = "https://kauth.kakao.com";

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialProfile extract(Map<String, Object> attributes) {
        String issuer = OAuth2ProfileAttributes.asString(attributes.get("iss"));
        if (!ISSUER.equals(issuer)) {
            throw new IllegalArgumentException("Kakao OIDC issuer does not match");
        }
        return new SocialProfile(
                provider(),
                OAuth2ProfileAttributes.asString(attributes.get("sub")),
                OAuth2ProfileAttributes.asString(attributes.get("email")),
                OAuth2ProfileAttributes.fallback(
                        OAuth2ProfileAttributes.asString(attributes.get("name")),
                        OAuth2ProfileAttributes.asString(attributes.get("nickname"))
                ),
                OAuth2ProfileAttributes.asString(attributes.get("picture")),
                issuer,
                OAuth2ProfileAttributes.firstBoolean(attributes, "email_verified")
        );
    }

    @Override
    public String defaultNameAttributeKey() {
        return "sub";
    }
}
