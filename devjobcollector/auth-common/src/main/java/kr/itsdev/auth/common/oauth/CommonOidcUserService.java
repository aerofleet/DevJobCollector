package kr.itsdev.auth.common.oauth;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Runs after Spring Security has validated the OIDC ID Token and nonce.
 * It maps verified claims into DJC's provider-neutral identity contract.
 */
public final class CommonOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private static final Logger log = LoggerFactory.getLogger(CommonOidcUserService.class);
    private static final String FALLBACK_ERROR_CODE = "OAUTH_LOGIN_FAILED";
    private static final Set<String> USER_INFO_SCOPES = Set.of(
            "profile",
            "email",
            "address",
            "phone",
            "profile_nickname",
            "profile_image",
            "account_email"
    );

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final SocialUserUpsertService socialUserUpsertService;
    private final OAuthProviderRegistry providerRegistry;

    public CommonOidcUserService(
            SocialUserUpsertService socialUserUpsertService,
            OAuthProviderRegistry providerRegistry
    ) {
        this(socialUserUpsertService, providerRegistry, defaultOidcUserService());
    }

    static OidcUserService defaultOidcUserService() {
        OidcUserService delegate = new OidcUserService();
        delegate.setRetrieveUserInfo(request -> request.getAccessToken().getScopes().stream()
                .anyMatch(USER_INFO_SCOPES::contains));
        return delegate;
    }

    CommonOidcUserService(
            SocialUserUpsertService socialUserUpsertService,
            OAuthProviderRegistry providerRegistry,
            OAuth2UserService<OidcUserRequest, OidcUser> delegate
    ) {
        this.socialUserUpsertService = socialUserUpsertService;
        this.providerRegistry = providerRegistry;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        try {
            return loadVerifiedUser(userRequest);
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "OIDC user processing failed: provider={}, exceptionType={}, rootCauseType={}",
                    registrationId(userRequest),
                    exception.getClass().getName(),
                    rootCauseType(exception)
            );
            throw new OAuth2AuthenticationException(new OAuth2Error(FALLBACK_ERROR_CODE), exception);
        }
    }

    private OidcUser loadVerifiedUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        OAuth2ProfileAdapter adapter = providerRegistry.require(
                userRequest.getClientRegistration().getRegistrationId()
        );
        Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
        SocialProfile profile = adapter.extract(claims);
        AuthenticatedUser appUser = socialUserUpsertService.upsert(profile);

        claims.put(AuthCommonAttributeKeys.APP_USER_ID, appUser.id());
        claims.put(AuthCommonAttributeKeys.APP_USER_EMAIL, appUser.email());
        claims.put(AuthCommonAttributeKeys.APP_USER_NAME, appUser.name());
        claims.put(AuthCommonAttributeKeys.APP_USER_ROLE, appUser.role());

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(oidcUser.getAuthorities());
        String role = appUser.role() == null || appUser.role().isBlank() ? "USER" : appUser.role();
        authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));

        String configuredNameKey = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
        String nameKey = configuredNameKey == null || configuredNameKey.isBlank()
                ? adapter.defaultNameAttributeKey()
                : configuredNameKey;
        if (!claims.containsKey(nameKey)) {
            nameKey = adapter.defaultNameAttributeKey();
        }

        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                new OidcUserInfo(claims),
                nameKey
        );
    }

    private String registrationId(OidcUserRequest userRequest) {
        if (userRequest == null || userRequest.getClientRegistration() == null) {
            return "unknown";
        }
        return userRequest.getClientRegistration().getRegistrationId();
    }

    private String rootCauseType(RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }
}
