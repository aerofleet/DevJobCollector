package kr.itsdev.devjobcollector.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminSecurityDomainTest {
    @Test
    void normalizesEmailAndProtectsMfaCiphertextFromMutation() {
        AdminAccount account = account(" Security.Admin@Example.COM ");
        byte[] ciphertext = {1, 2, 3};

        account.configureMfa(ciphertext, "key-v1");
        ciphertext[0] = 9;
        byte[] returned = account.getMfaSecretCiphertext();
        returned[1] = 9;

        assertThat(account.getEmail()).isEqualTo("security.admin@example.com");
        assertThat(account.getMfaSecretCiphertext()).containsExactly(1, 2, 3);
        assertThat(account.getCredentialVersion()).isEqualTo(1);
    }

    @Test
    void sessionAcceptsOnlyHashesAndUsesExclusiveExpiry() {
        LocalDateTime issuedAt = LocalDateTime.of(2026, 9, 24, 10, 0);
        AdminSession session = AdminSession.issue(
                account("admin@example.com"), "A".repeat(64), "b".repeat(64),
                issuedAt, issuedAt.plusMinutes(30));

        assertThat(session.getRefreshHash()).isEqualTo("a".repeat(64));
        assertThat(session.isUsableAt(issuedAt.plusMinutes(29))).isTrue();
        assertThat(session.isUsableAt(issuedAt.plusMinutes(30))).isFalse();
        assertThatThrownBy(() -> AdminSession.issue(
                account("other@example.com"), "raw-token", "b".repeat(64),
                issuedAt, issuedAt.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokedSessionCannotBeUsed() {
        LocalDateTime issuedAt = LocalDateTime.of(2026, 9, 24, 10, 0);
        AdminSession session = AdminSession.issue(
                account("admin@example.com"), "a".repeat(64), "b".repeat(64),
                issuedAt, issuedAt.plusHours(1));

        session.revoke(issuedAt.plusMinutes(5));

        assertThat(session.isUsableAt(issuedAt.plusMinutes(6))).isFalse();
    }

    @Test
    void auditRequiresRequestCorrelationAndStructuredResult() {
        assertThatThrownBy(() -> AdminAuditLog.record(
                1L, "USER_SUSPEND", "USER", "42", "reason", null, null,
                AdminAuditResult.SUCCESS, " ", "127.0.0.1", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId is required");
    }

    private AdminAccount account(String email) {
        return AdminAccount.active(email, "$2a$12$" + "a".repeat(53),
                "보안 관리자", AdminRole.ADMIN);
    }
}
