package kr.itsdev.devjobcollector.security.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;
import kr.itsdev.devjobcollector.dto.auth.AccountLinkStartResponse;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import kr.itsdev.devjobcollector.security.account.UserIdentity;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountLinkService {
    public static final String LINK_INVALID = "ACCOUNT_LINK_INVALID";
    public static final String LINK_CONFLICT = "ACCOUNT_LINK_CONFLICT";

    private static final String SESSION_ATTRIBUTE = AccountLinkService.class.getName() + ".intent";
    private static final Duration INTENT_TTL = Duration.ofMinutes(5);
    private static final Duration REAUTH_MAX_AGE = Duration.ofMinutes(5);

    private final UserAccountRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final Clock clock;

    @Autowired
    public AccountLinkService(
            UserAccountRepository userRepository,
            UserIdentityRepository identityRepository
    ) {
        this(userRepository, identityRepository, Clock.systemUTC());
    }

    AccountLinkService(
            UserAccountRepository userRepository,
            UserIdentityRepository identityRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountLinkStartResponse start(
            String subject,
            String providerValue,
            Instant authenticatedAt,
            HttpServletRequest request
    ) {
        requireRecentAuthentication(authenticatedAt);
        AuthProvider provider = socialProvider(providerValue);
        UserAccount user = activeUser(subject);
        if (identityRepository.existsByUserAndProvider(user, provider)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED");
        }

        Instant expiresAt = clock.instant().plus(INTENT_TTL);
        request.getSession(true).setAttribute(
                SESSION_ATTRIBUTE,
                new LinkIntent(user.getId(), provider, expiresAt)
        );
        return new AccountLinkStartResponse(
                "/oauth2/authorization/" + provider.name().toLowerCase(Locale.ROOT),
                Math.toIntExact(INTENT_TTL.toSeconds())
        );
    }

    @Transactional
    public Optional<AuthenticatedUser> linkIfRequested(SocialProfile profile) {
        HttpSession session = currentSession();
        if (session == null) {
            return Optional.empty();
        }
        Object rawIntent = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(rawIntent instanceof LinkIntent intent)) {
            return Optional.empty();
        }

        session.removeAttribute(SESSION_ATTRIBUTE);
        AuthProvider callbackProvider = AuthProvider.valueOf(profile.provider().name());
        if (intent.expiresAt().isBefore(clock.instant()) || intent.provider() != callbackProvider) {
            throw oauthError(LINK_INVALID);
        }

        UserAccount target = userRepository.findById(intent.userId())
                .filter(user -> user.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(() -> oauthError(LINK_INVALID));

        String providerEmail = normalizeEmail(profile.email());
        if (!Boolean.TRUE.equals(profile.emailVerified())
                || providerEmail == null
                || !providerEmail.equals(normalizeEmail(target.getEmail()))) {
            throw oauthError(LINK_CONFLICT);
        }

        String providerSubject = requireProviderSubject(profile.providerUserId());
        UserIdentity subjectOwner = identityRepository
                .findByProviderAndProviderSubject(callbackProvider, providerSubject)
                .orElse(null);
        if (subjectOwner != null) {
            throw oauthError(LINK_CONFLICT);
        }
        if (identityRepository.existsByUserAndProvider(target, callbackProvider)) {
            throw oauthError(LINK_CONFLICT);
        }

        UserIdentity identity = UserIdentity.social(
                target,
                callbackProvider,
                providerSubject,
                profile.issuer(),
                providerEmail,
                true
        );
        identity.recordSuccessfulLogin(LocalDateTime.now(clock));
        identityRepository.save(identity);

        return Optional.of(new AuthenticatedUser(
                target.getId(), target.getEmail(), target.getName(), target.getRole()));
    }

    private HttpSession currentSession() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest().getSession(false);
    }

    private UserAccount activeUser(String subject) {
        Long userId;
        try {
            userId = Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MEMBER_SUBJECT_INVALID");
        }
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "MEMBER_ACCOUNT_INACTIVE"));
    }

    private void requireRecentAuthentication(Instant authenticatedAt) {
        Instant now = clock.instant();
        if (authenticatedAt == null
                || authenticatedAt.isBefore(now.minus(REAUTH_MAX_AGE))
                || authenticatedAt.isAfter(now.plusSeconds(30))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ACCOUNT_REAUTH_REQUIRED");
        }
    }

    private AuthProvider socialProvider(String value) {
        try {
            AuthProvider provider = AuthProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (provider == AuthProvider.GOOGLE || provider == AuthProvider.GITHUB) {
                return provider;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Stable 400 response below.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LINK_PROVIDER");
    }

    private String requireProviderSubject(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 255) {
            throw oauthError(LINK_INVALID);
        }
        return value.trim();
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private OAuth2AuthenticationException oauthError(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), code);
    }

    private record LinkIntent(Long userId, AuthProvider provider, Instant expiresAt) implements Serializable {
    }
}
