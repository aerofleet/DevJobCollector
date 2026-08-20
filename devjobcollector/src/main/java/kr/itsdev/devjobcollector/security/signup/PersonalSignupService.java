package kr.itsdev.devjobcollector.security.signup;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.spi.TokenIssueService;
import kr.itsdev.devjobcollector.dto.auth.LoginResponse;
import kr.itsdev.devjobcollector.dto.auth.PersonalSignupRequest;
import kr.itsdev.devjobcollector.dto.auth.PersonalSignupResponse;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.EmailVerificationToken;
import kr.itsdev.devjobcollector.security.account.EmailVerificationTokenRepository;
import kr.itsdev.devjobcollector.security.account.PersonalProfile;
import kr.itsdev.devjobcollector.security.account.PersonalProfileRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import kr.itsdev.devjobcollector.security.account.UserIdentity;
import kr.itsdev.devjobcollector.security.account.UserIdentityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalSignupService {
    private final UserAccountRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PersonalProfileRepository profileRepository;
    private final UserIdentityRepository identityRepository;
    private final RequiredConsentService requiredConsentService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final TurnstileVerifier turnstileVerifier;
    private final SignupRateLimiter rateLimiter;
    private final DisposableEmailPolicy disposableEmailPolicy;
    private final AuthSignupProperties properties;
    private final TokenIssueService tokenIssueService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalSignupService(
            UserAccountRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            PersonalProfileRepository profileRepository,
            UserIdentityRepository identityRepository,
            RequiredConsentService requiredConsentService,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            TurnstileVerifier turnstileVerifier,
            SignupRateLimiter rateLimiter,
            DisposableEmailPolicy disposableEmailPolicy,
            AuthSignupProperties properties,
            TokenIssueService tokenIssueService
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.profileRepository = profileRepository;
        this.identityRepository = identityRepository;
        this.requiredConsentService = requiredConsentService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.turnstileVerifier = turnstileVerifier;
        this.rateLimiter = rateLimiter;
        this.disposableEmailPolicy = disposableEmailPolicy;
        this.properties = properties;
        this.tokenIssueService = tokenIssueService;
    }

    @Transactional
    public PersonalSignupResponse signup(PersonalSignupRequest request, String remoteIp) {
        requiredConsentService.validateAccepted(request.termsAccepted(), request.privacyAccepted());
        String email = normalizeEmail(request.email());
        rateLimiter.check(remoteIp, email);
        turnstileVerifier.verify(request.turnstileToken(), remoteIp);
        disposableEmailPolicy.validate(email);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        UserAccount user = userRepository.save(UserAccount.pendingLocal(
                email,
                request.name().trim(),
                passwordEncoder.encode(request.password())
        ));
        identityRepository.save(UserIdentity.local(user));
        requiredConsentService.recordAccepted(user);
        String code = createVerificationToken(user);
        eventPublisher.publishEvent(new VerificationCodeIssuedEvent(email, code));
        return response(email, code);
    }

    @Transactional
    public PersonalSignupResponse resend(String emailValue, String turnstileToken, String remoteIp) {
        String email = normalizeEmail(emailValue);
        rateLimiter.check(remoteIp, email);
        turnstileVerifier.verify(turnstileToken, remoteIp);
        UserAccount user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가입 정보를 찾을 수 없습니다."));
        if (user.getStatus() == UserAccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 인증된 계정입니다.");
        }
        tokenRepository.findFirstByUserAndUsedAtIsNullOrderByIdDesc(user)
                .ifPresent(EmailVerificationToken::markUsed);
        String code = createVerificationToken(user);
        eventPublisher.publishEvent(new VerificationCodeIssuedEvent(email, code));
        return response(email, code);
    }

    @Transactional
    public LoginResponse verifyEmail(String emailValue, String code) {
        String email = normalizeEmail(emailValue);
        UserAccount user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 정보가 올바르지 않습니다."));
        if (user.getStatus() == UserAccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 인증된 계정입니다.");
        }
        EmailVerificationToken token = tokenRepository.findFirstByUserAndUsedAtIsNullOrderByIdDesc(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 인증 코드가 없습니다."));
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "인증 코드가 만료되었습니다.");
        }
        if (token.getAttempts() >= properties.getMaxVerificationAttempts()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다.");
        }
        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            token.recordFailedAttempt();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다.");
        }

        token.markUsed();
        user.activateEmail();
        identityRepository.findByUserAndProvider(user, AuthProvider.LOCAL)
                .orElseThrow(() -> new IllegalStateException("LOCAL identity is missing"))
                .updateProviderEmail(user.getEmail(), true);
        if (!profileRepository.existsByUser(user)) {
            profileRepository.save(PersonalProfile.active(user));
        }
        String accessToken = tokenIssueService.issueAccessToken(authenticated(user));
        return new LoginResponse(accessToken, "Bearer");
    }

    private String createVerificationToken(UserAccount user) {
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        tokenRepository.save(new EmailVerificationToken(
                user,
                passwordEncoder.encode(code),
                LocalDateTime.now().plusMinutes(properties.getVerificationMinutes())
        ));
        return code;
    }

    private PersonalSignupResponse response(String email, String code) {
        return new PersonalSignupResponse(
                email,
                UserAccountStatus.PENDING_EMAIL.name(),
                properties.getVerificationMinutes(),
                properties.isExposeCodeInResponse() ? code : null
        );
    }

    private AuthenticatedUser authenticated(UserAccount user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
