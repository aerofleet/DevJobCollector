package kr.itsdev.devjobcollector.admin.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminAuditLog;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminUserServiceTest {
    private final UserAccountRepository users = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final UserModerationHistoryRepository history =
            org.mockito.Mockito.mock(UserModerationHistoryRepository.class);
    private final AdminAuditLogRepository audit = org.mockito.Mockito.mock(AdminAuditLogRepository.class);
    private final AdminUserService service = new AdminUserService(users, history, audit);
    private final AdminPrincipal admin = new AdminPrincipal(4L, "admin@example.com", "Admin",
            AdminRole.ADMIN, Set.of(), "session", "csrf");

    @Test
    void suspendsAndRecordsHistoryAndAudit() {
        UserAccount user = UserAccount.activeSocial("member@example.com", "Member",
                kr.itsdev.devjobcollector.security.account.AuthProvider.GITHUB, "subject");
        when(users.findByIdForModeration(7L)).thenReturn(Optional.of(user));

        var result = service.moderate(7L, UserAccountStatus.SUSPENDED, 0, "abuse", admin,
                "request", "127.0.0.1", "test");

        assertThat(result.status()).isEqualTo("SUSPENDED");
        assertThat(user.getSessionRevokedAt()).isNotNull();
        verify(history).save(any(UserModerationHistory.class));
        verify(audit).save(any(AdminAuditLog.class));
    }

    @Test
    void rejectsStaleVersionWithoutWritingHistory() {
        UserAccount user = UserAccount.activeSocial("member@example.com", "Member",
                kr.itsdev.devjobcollector.security.account.AuthProvider.GITHUB, "subject");
        when(users.findByIdForModeration(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.moderate(7L, UserAccountStatus.SUSPENDED, 1,
                "abuse", admin, "request", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USER_VERSION_CONFLICT");
        verify(history, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void reviewerCannotModerate() {
        AdminPrincipal reviewer = new AdminPrincipal(5L, "reviewer@example.com", "Reviewer",
                AdminRole.REVIEWER, Set.of(), "session", "csrf");
        assertThatThrownBy(() -> service.moderate(7L, UserAccountStatus.SUSPENDED, 0,
                "abuse", reviewer, "request", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ADMIN_ROLE_DENIED");
        verify(users, never()).findByIdForModeration(any());
    }
}
