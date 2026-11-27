package kr.itsdev.devjobcollector.security.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemberLoginLockTest {
    @Test
    void lockExpiresAndOldFailuresDoNotAccumulate() {
        var user = UserAccount.pendingLocal("user@example.com", "User", "hash");
        var now = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 4; i++) user.recordLoginFailure(now);
        user.recordLoginFailure(now.plusMinutes(16));
        assertThat(user.isLoginLocked(now.plusMinutes(16))).isFalse();
        for (int i = 0; i < 4; i++) user.recordLoginFailure(now.plusMinutes(16));
        assertThat(user.isLoginLocked(now.plusMinutes(30))).isTrue();
        assertThat(user.isLoginLocked(now.plusMinutes(31))).isFalse();
        user.recordLoginFailure(now.plusMinutes(31));
        assertThat(user.isLoginLocked(now.plusMinutes(31))).isFalse();
        user.clearLoginFailures();
        for (int i = 0; i < 4; i++) user.recordLoginFailure(now.plusMinutes(32));
        assertThat(user.isLoginLocked(now.plusMinutes(32))).isFalse();
    }
}
