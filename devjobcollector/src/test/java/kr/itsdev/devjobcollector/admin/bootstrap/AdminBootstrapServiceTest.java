package kr.itsdev.devjobcollector.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.itsdev.devjobcollector.admin.AdminAccount;
import kr.itsdev.devjobcollector.admin.AdminAccountRepository;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {
    @Mock AdminAccountRepository accountRepository;
    @Mock AdminAuditLogRepository auditLogRepository;
    @Mock PasswordEncoder passwordEncoder;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new AdminBootstrapService(accountRepository, auditLogRepository, passwordEncoder);
    }

    @Test
    void createsOnlyHashedSuperAdminAndAuditRecord() {
        AdminAccount persisted = org.mockito.Mockito.mock(AdminAccount.class);
        when(persisted.getId()).thenReturn(7L);
        when(accountRepository.findByEmail("security.admin@example.com"))
                .thenReturn(Optional.empty());
        when(accountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        when(accountRepository.saveAndFlush(any())).thenReturn(persisted);
        char[] password = "long-bootstrap-passphrase".toCharArray();

        AdminBootstrapResult result = service.provision(
                " Security.Admin@Example.COM ", "운영 관리자", password);

        assertThat(result).isEqualTo(AdminBootstrapResult.CREATED);
        ArgumentCaptor<CharSequence> passwordCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getValue().toString()).isEqualTo("long-bootstrap-passphrase");
        ArgumentCaptor<AdminAccount> accountCaptor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).isEqualTo("security.admin@example.com");
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(accountCaptor.getValue().getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("ADMIN_BOOTSTRAP");
        assertThat(auditCaptor.getValue().getActorAdminId()).isEqualTo(7L);
        assertThat(auditCaptor.getValue().getAfterJson()).doesNotContain("password");
    }

    @Test
    void treatsExistingSuperAdminAsIdempotentSuccess() {
        AdminAccount existing = AdminAccount.active(
                "admin@example.com", "existing-hash", "관리자", AdminRole.SUPER_ADMIN);
        when(accountRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        AdminBootstrapResult result = service.provision(
                "admin@example.com", "관리자", "long-bootstrap-passphrase".toCharArray());

        assertThat(result).isEqualTo(AdminBootstrapResult.ALREADY_EXISTS);
        verify(passwordEncoder, never()).encode(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void refusesToBootstrapIntoNonEmptyAdminStore() {
        when(accountRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(accountRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.provision(
                "admin@example.com", "관리자", "long-bootstrap-passphrase".toCharArray()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty admin store");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void rejectsShortOrControlCharacterPasswordBeforeHashing() {
        when(accountRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(accountRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> service.provision(
                "admin@example.com", "관리자", "too-short".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 to 128");
        assertThatThrownBy(() -> service.provision(
                "admin@example.com", "관리자", "valid-length-but\ninvalid".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
        verify(passwordEncoder, never()).encode(any());
    }
}
