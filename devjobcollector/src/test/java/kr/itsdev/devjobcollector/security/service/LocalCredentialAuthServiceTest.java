package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.devjobcollector.security.AuthLocalLoginProperties;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LocalCredentialAuthServiceTest {
    @Mock UserAccountRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    private AuthLocalLoginProperties properties;
    private LocalCredentialAuthService service;

    @BeforeEach
    void setUp() {
        properties = new AuthLocalLoginProperties();
        service = new LocalCredentialAuthService(properties, userRepository, passwordEncoder);
    }

    @Test
    void configurationFallbackIsDisabledAndEmptyByDefault() {
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getUsers()).isEmpty();
    }

    @Test
    void formerDefaultCredentialIsRejected() {
        when(userRepository.findByEmailIgnoreCase("admin")).thenReturn(Optional.empty());

        assertThat(service.authenticate("admin", "admin1234")).isNull();
    }

    @Test
    void databaseBackedLocalAccountStillAuthenticates() {
        UserAccount account = UserAccount.pendingLocal("user@example.com", "사용자", "encoded-password");
        account.activateEmail();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);

        AuthenticatedUser authenticated = service.authenticate(" USER@example.com ", "correct-password");

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.email()).isEqualTo("user@example.com");
    }

    @Test
    void explicitlyConfiguredFallbackRemainsOptIn() {
        AuthLocalLoginProperties.User fallback = new AuthLocalLoginProperties.User();
        fallback.setId(9001L);
        fallback.setUsername("break-glass");
        fallback.setEmail("break-glass@example.invalid");
        fallback.setPassword("explicit-secret");
        fallback.setName("Break Glass");
        fallback.setRole("PLATFORM_ADMIN");
        properties.setEnabled(true);
        properties.getUsers().add(fallback);
        when(userRepository.findByEmailIgnoreCase("break-glass")).thenReturn(Optional.empty());

        AuthenticatedUser authenticated = service.authenticate("break-glass", "explicit-secret");

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.id()).isEqualTo(9001L);
        assertThat(authenticated.role()).isEqualTo("PLATFORM_ADMIN");
    }
}
