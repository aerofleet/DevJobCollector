package kr.itsdev.auth.common.oauth;

import java.util.Map;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;

/** Converts one provider's user-info response into the provider-neutral profile contract. */
public interface OAuth2ProfileAdapter {
    SocialProvider provider();

    SocialProfile extract(Map<String, Object> attributes);

    String defaultNameAttributeKey();
}
