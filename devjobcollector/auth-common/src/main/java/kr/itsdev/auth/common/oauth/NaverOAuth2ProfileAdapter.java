package kr.itsdev.auth.common.oauth;

import java.util.HashMap;
import java.util.Map;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;

/** Converts Naver's nested user-info response into the common social profile contract. */
public final class NaverOAuth2ProfileAdapter implements OAuth2ProfileAdapter {
    private static final String SUCCESS_RESULT_CODE = "00";

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public SocialProfile extract(Map<String, Object> attributes) {
        String resultCode = OAuth2ProfileAttributes.asString(attributes.get("resultcode"));
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            throw new IllegalArgumentException("Naver user-info result is not successful");
        }

        Map<String, Object> response = response(attributes.get("response"));
        String providerUserId = OAuth2ProfileAttributes.asString(response.get("id"));
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("Naver user-info id is required");
        }

        return new SocialProfile(
                provider(),
                providerUserId,
                OAuth2ProfileAttributes.asString(response.get("email")),
                OAuth2ProfileAttributes.fallback(
                        OAuth2ProfileAttributes.asString(response.get("name")),
                        OAuth2ProfileAttributes.asString(response.get("nickname"))
                ),
                OAuth2ProfileAttributes.asString(response.get("profile_image")),
                null,
                null
        );
    }

    @Override
    public String defaultNameAttributeKey() {
        return "response";
    }

    private Map<String, Object> response(Object value) {
        if (!(value instanceof Map<?, ?> rawResponse)) {
            throw new IllegalArgumentException("Naver user-info response is required");
        }
        HashMap<String, Object> response = new HashMap<>();
        rawResponse.forEach((key, item) -> {
            if (key instanceof String stringKey) {
                response.put(stringKey, item);
            }
        });
        return response;
    }
}
