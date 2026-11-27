package kr.itsdev.devjobcollector.security.service;

import java.util.Locale;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LocalCredentialAuthService {
    private static final Logger log = LoggerFactory.getLogger(LocalCredentialAuthService.class);
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalCredentialAuthService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthenticatedUser authenticate(String identifier, String password) {
        if (identifier == null || password == null) {
            return null;
        }

        String normalizedIdentifier = identifier.trim().toLowerCase(Locale.ROOT);
        UserAccount account = userRepository.findByEmailForUpdate(normalizedIdentifier).orElse(null);
        if (account != null && account.getPasswordHash() != null) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            if (account.getStatus() != UserAccountStatus.ACTIVE || account.isLoginLocked(now)) {
                if (account.isLoginLocked(now)) log.warn("MEMBER_LOGIN_LOCKED userId={}", account.getId());
                return null;
            }
            if (!passwordEncoder.matches(password, account.getPasswordHash())) {
                account.recordLoginFailure(now);
                log.warn("MEMBER_LOGIN_FAILURE userId={} locked={}", account.getId(), account.isLoginLocked(now));
                return null;
            }
            account.clearLoginFailures();
            return new AuthenticatedUser(account.getId(), account.getEmail(), account.getName(), account.getRole());
        }
        return null;
    }
}
