package kr.itsdev.devjobcollector.security.service;

import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InMemorySocialUserUpsertService implements SocialUserUpsertService {
    private final UserAccountRepository userRepository;

    public InMemorySocialUserUpsertService(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public AuthenticatedUser upsert(SocialProfile profile) {
        AuthProvider provider = AuthProvider.valueOf(profile.provider().name());
        UserAccount account = userRepository.findByProviderAndProviderUserId(provider, profile.providerUserId())
                .orElseGet(() -> profile.email() == null ? null
                        : userRepository.findByEmailIgnoreCase(profile.email()).orElse(null));
        String fallbackKey = profile.provider().name().toLowerCase() + "-" + profile.providerUserId() + "@social.local";
        String email = normalizeEmail(profile.email(), fallbackKey);
        String name = normalizeName(profile.name(), profile.provider().name() + " user");
        if (account == null) {
            account = UserAccount.activeSocial(email, name, provider, profile.providerUserId());
        } else {
            account.updateSocialProfile(email, name, provider, profile.providerUserId());
        }
        UserAccount saved = userRepository.save(account);
        return new AuthenticatedUser(saved.getId(), saved.getEmail(), saved.getName(), saved.getRole());
    }

    private String normalizeEmail(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
