package kr.itsdev.auth.common.oauth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.itsdev.auth.common.model.SocialProvider;

/** Immutable provider registry. New providers become active only when an adapter is registered. */
public final class OAuthProviderRegistry {
    private final Map<SocialProvider, OAuth2ProfileAdapter> adapters;

    public OAuthProviderRegistry(Collection<? extends OAuth2ProfileAdapter> adapters) {
        EnumMap<SocialProvider, OAuth2ProfileAdapter> indexed = new EnumMap<>(SocialProvider.class);
        for (OAuth2ProfileAdapter adapter : adapters) {
            if (adapter == null || adapter.provider() == null) {
                throw new IllegalArgumentException("OAuth provider adapter and provider are required");
            }
            if (indexed.putIfAbsent(adapter.provider(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate OAuth provider adapter: " + adapter.provider());
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    public static OAuthProviderRegistry defaults() {
        return defaults(List.of());
    }

    public static OAuthProviderRegistry defaults(
            Collection<? extends OAuth2ProfileAdapter> additionalAdapters
    ) {
        List<OAuth2ProfileAdapter> adapters = new ArrayList<>(List.of(
                new GoogleOAuth2ProfileAdapter(),
                new GithubOAuth2ProfileAdapter(),
                new KakaoOidcProfileAdapter()
        ));
        adapters.addAll(additionalAdapters);
        return new OAuthProviderRegistry(adapters);
    }

    public OAuth2ProfileAdapter require(String registrationId) {
        return require(SocialProvider.fromRegistrationId(registrationId));
    }

    public OAuth2ProfileAdapter require(SocialProvider provider) {
        OAuth2ProfileAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported provider: " + provider.name().toLowerCase());
        }
        return adapter;
    }

    public Set<SocialProvider> activeProviders() {
        return adapters.keySet();
    }
}
