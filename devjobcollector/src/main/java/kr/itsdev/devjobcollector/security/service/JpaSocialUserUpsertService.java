package kr.itsdev.devjobcollector.security.service;

import java.time.LocalDateTime;
import java.util.Locale;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.auth.common.spi.SocialUserUpsertService;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import kr.itsdev.devjobcollector.security.account.UserIdentity;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JpaSocialUserUpsertService implements SocialUserUpsertService {
    private final UserAccountRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final PersonalProfileRepository profileRepository;

    public JpaSocialUserUpsertService(
            UserAccountRepository userRepository,
            UserIdentityRepository identityRepository,
            PersonalProfileRepository profileRepository
    ) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional
    public AuthenticatedUser upsert(SocialProfile profile) {
        AuthProvider provider = AuthProvider.valueOf(profile.provider().name());
        String providerSubject = requireProviderSubject(profile.providerUserId());
        UserIdentity identity = identityRepository
                .findByProviderAndProviderSubject(provider, providerSubject)
                .orElse(null);

        UserAccount account;
        if (identity == null) {
            account = findLegacyEmailMatch(profile.email());
            if (account != null) {
                requireActive(account);
            }
            String fallbackEmail = provider.name().toLowerCase(Locale.ROOT)
                    + "-" + providerSubject + "@social.local";
            String email = normalizeEmail(profile.email(), fallbackEmail);
            String name = normalizeName(profile.name(), provider.name() + " user");
            if (account == null) {
                account = UserAccount.activeSocial(email, name, provider, null);
            } else {
                account.updateProfileAfterSocialAuthentication(email, name);
            }
            account = userRepository.save(account);
            identity = identityRepository.save(UserIdentity.social(
                    account,
                    provider,
                    providerSubject,
                    profile.issuer(),
                    normalizeOptionalEmail(profile.email()),
                    profile.emailVerified()
            ));
        } else {
            account = identity.getUser();
            requireActive(account);
            String email = normalizeEmail(profile.email(), account.getEmail());
            String name = normalizeName(profile.name(), account.getName());
            account.updateProfileAfterSocialAuthentication(email, name);
            identity.updateProviderEmail(normalizeOptionalEmail(profile.email()), profile.emailVerified());
        }

        identity.recordSuccessfulLogin(LocalDateTime.now());
        if (!profileRepository.existsByUser(account)) {
            profileRepository.save(PersonalProfile.active(account));
        }
        return new AuthenticatedUser(account.getId(), account.getEmail(), account.getName(), account.getRole());
    }

    // Transitional compatibility only. P2-03 replaces this with ACCOUNT_LINK_REQUIRED.
    private UserAccount findLegacyEmailMatch(String email) {
        String normalizedEmail = normalizeOptionalEmail(email);
        return normalizedEmail == null
                ? null
                : userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
    }

    private void requireActive(UserAccount account) {
        if (account.getStatus() != UserAccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
    }

    private String requireProviderSubject(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("providerSubject is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("providerSubject exceeds 255 characters");
        }
        return trimmed;
    }

    private String normalizeOptionalEmail(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value, String fallback) {
        String normalized = normalizeOptionalEmail(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalizeName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
