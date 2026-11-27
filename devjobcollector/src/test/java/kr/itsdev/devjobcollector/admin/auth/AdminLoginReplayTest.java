package kr.itsdev.devjobcollector.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.Optional;
import kr.itsdev.devjobcollector.admin.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminLoginReplayTest {
    @Test
    void requiresBothFactorsAndConsumesEachCounterOnce() {
        var accounts = mock(AdminAccountRepository.class);
        var sessions = mock(AdminSessionRepository.class);
        var audits = mock(AdminAuditLogRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var cipher = mock(AdminMfaSecretCipher.class);
        var totp = mock(AdminTotpVerifier.class);
        var codec = new AdminTokenCodec();
        var account = AdminAccount.active("admin@example.com", "hash", "Admin", AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(account, "id", 1L);
        account.configureMfa(new byte[] {1}, "key");
        when(accounts.findByEmailForUpdate(anyString())).thenReturn(Optional.of(account));
        when(passwords.matches(anyString(), anyString())).thenAnswer(i -> i.getArgument(0).equals("correct"));
        when(cipher.decrypt(any())).thenReturn("secret");
        when(totp.matchingCounter(eq("secret"), eq("000000"))).thenReturn(null);
        when(totp.matchingCounter(eq("secret"), eq("123456"))).thenReturn(100L);
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        var service = new AdminAuthenticationService(accounts, sessions, audits, passwords, cipher,
                totp, codec, new AdminSecurityProperties(), Clock.systemUTC());

        assertThat(service.login("admin@example.com", "wrong", "123456", "r", "ip", "ua").status())
                .isEqualTo(AdminLoginResult.Status.INVALID_CREDENTIALS);
        assertThat(service.login("admin@example.com", "correct", "000000", "r", "ip", "ua").status())
                .isEqualTo(AdminLoginResult.Status.INVALID_CREDENTIALS);
        assertThat(service.login("admin@example.com", "correct", "123456", "r", "ip", "ua").status())
                .isEqualTo(AdminLoginResult.Status.SUCCESS);
        assertThat(service.login("admin@example.com", "correct", "123456", "r", "ip", "ua").status())
                .isEqualTo(AdminLoginResult.Status.INVALID_CREDENTIALS);
        verify(sessions, times(1)).save(any());
    }
}
