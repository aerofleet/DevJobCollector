package kr.itsdev.auth.common.oauth;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.model.SocialProvider;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class CommonOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final SocialUserUpsertService socialUserUpsertService;

    public CommonOAuth2UserService(SocialUserUpsertService socialUserUpsertService) {
        this.socialUserUpsertService = socialUserUpsertService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());

        SocialProvider provider = SocialProvider.fromRegistrationId(
                userRequest.getClientRegistration().getRegistrationId()
        );
        SocialProfile profile = OAuth2ProfileExtractor.extract(provider, attributes);
        AuthenticatedUser appUser = socialUserUpsertService.upsert(profile);

        attributes.put(AuthCommonAttributeKeys.APP_USER_ID, appUser.id());
        attributes.put(AuthCommonAttributeKeys.APP_USER_EMAIL, appUser.email());
        attributes.put(AuthCommonAttributeKeys.APP_USER_NAME, appUser.name());
        attributes.put(AuthCommonAttributeKeys.APP_USER_ROLE, appUser.role());

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(oauth2User.getAuthorities());
        String role = appUser.role() == null || appUser.role().isBlank() ? "USER" : appUser.role();
        authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));

        String configuredNameKey = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
        String nameKey = (configuredNameKey == null || configuredNameKey.isBlank())
                ? OAuth2ProfileExtractor.defaultNameAttributeKey(provider)
                : configuredNameKey;

        if (!attributes.containsKey(nameKey)) {
            nameKey = OAuth2ProfileExtractor.defaultNameAttributeKey(provider);
        }

        return new DefaultOAuth2User(authorities, attributes, nameKey);
    }
}
