package kr.itsdev.devjobcollector.admin.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, AdminUserService.class})
class AdminUserIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired AdminUserService service;

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @Test
    void listsMembersAndCommitsModerationHistoryAuditAndRevocation() {
        jdbc.update("""
                INSERT INTO admin_accounts (id, email, password_hash, name, role, status)
                VALUES (909, 'moderator@example.com', ?, 'Moderator', 'ADMIN', 'ACTIVE')
                """, "p".repeat(60));
        UserAccount member = users.saveAndFlush(UserAccount.activeSocial(
                "member-list@example.com", "Member", AuthProvider.GITHUB, "member-list-subject"));
        Long memberId = member.getId();
        AdminPrincipal admin = new AdminPrincipal(909L, "moderator@example.com", "Moderator",
                AdminRole.ADMIN, Set.of(), "session", "csrf");

        var page = service.list("member-list@", UserAccountStatus.ACTIVE, 0, 20);
        assertThat(page.getContent()).extracting(AdminUserService.UserView::id).contains(memberId);
        var changed = service.moderate(memberId, UserAccountStatus.SUSPENDED,
                member.getVersion(), "policy violation", admin, "request-909", "127.0.0.1", "test");

        assertThat(changed.status()).isEqualTo("SUSPENDED");
        assertThat(changed.version()).isEqualTo(member.getVersion());
        assertThat(jdbc.queryForObject("SELECT session_revoked_at FROM users WHERE id = ?",
                LocalDateTime.class, memberId)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_moderation_history WHERE user_id = ?",
                Long.class, memberId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs "
                + "WHERE target_type = 'USER' AND target_id = ? AND request_id = 'request-909'",
                Long.class, memberId.toString())).isEqualTo(1L);

        for (int i = 0; i < 3; i++) service.list("member-list@", null, 0, 20);
        var durations = new ArrayList<Long>();
        for (int i = 0; i < 30; i++) {
            long started = System.nanoTime();
            service.list("member-list@", null, 0, 20);
            durations.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(durations);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        System.out.printf("ADMIN_USER_LIST_P95_MS=%d%n", p95);
        assertThat(p95).isLessThanOrEqualTo(500L);
    }
}
