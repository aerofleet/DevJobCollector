package kr.itsdev.devjobcollector.admin.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminAccount;
import kr.itsdev.devjobcollector.admin.AdminAccountRepository;
import kr.itsdev.devjobcollector.admin.AdminAccountStatus;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.AdminSession;
import kr.itsdev.devjobcollector.admin.AdminSessionRepository;
import kr.itsdev.devjobcollector.admin.auth.AdminMfaSecretCipher;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.auth.AdminSecurityProperties;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "admin.security.mfa-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, AdminAccountManagementService.class,
        AdminAccountManagementIntegrationTest.Dependencies.class})
class AdminAccountManagementIntegrationTest {
    private static final String MFA_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Autowired AdminAccountManagementService service;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminSessionRepository sessions;
    @Autowired AdminAuditLogRepository audit;
    @Autowired AdminMfaSecretCipher cipher;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entityManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @Test
    void provisionsModeratorRevokesSessionsAndAuditsChanges() throws Exception {
        AdminAccount root = accounts.saveAndFlush(AdminAccount.active("root@example.com", "hash",
                "Root", AdminRole.SUPER_ADMIN));
        AdminPrincipal actor = principal(root.getId(), AdminRole.SUPER_ADMIN);
        var created = service.create(actor, "New.Admin@example.com", "New Admin", AdminRole.ADMIN,
                "long-test-password-123", MFA_SECRET, "create-1", "127.0.0.1", "test");
        entityManager.clear();
        AdminAccount stored = accounts.findById(created.id()).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("new.admin@example.com");
        assertThat(passwords.matches("long-test-password-123", stored.getPasswordHash())).isTrue();
        assertThat(cipher.decrypt(stored.getMfaSecretCiphertext())).isEqualTo(MFA_SECRET);
        assertThat(mapper.writeValueAsString(created))
                .doesNotContain("long-test-password-123", MFA_SECRET, "passwordHash", "mfaSecretCiphertext");
        assertThat(service.list(actor, "New.Admin", AdminRole.ADMIN, AdminAccountStatus.ACTIVE, 0, 20)
                .getTotalElements()).isEqualTo(1);
        for (int i = 0; i < 3; i++) service.list(actor, null, null, null, 0, 20);
        var durations = new ArrayList<Long>();
        for (int i = 0; i < 30; i++) {
            long started = System.nanoTime();
            service.list(actor, null, null, null, 0, 20);
            durations.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(durations);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        System.out.printf("ADMIN_ACCOUNT_LIST_P95_MS=%d%n", p95);
        assertThat(p95).isLessThanOrEqualTo(500L);

        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        AdminSession session = sessions.saveAndFlush(AdminSession.issue(stored,
                "a".repeat(64), "b".repeat(64), now.minusMinutes(1), now.plusHours(1)));
        var reviewer = service.changeRole(actor, created.id(), AdminRole.REVIEWER,
                created.version(), "duty change", "role-1", "127.0.0.1", "test");
        entityManager.clear();
        assertThat(sessions.findById(session.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(accounts.findById(created.id()).orElseThrow().getRole()).isEqualTo(AdminRole.REVIEWER);

        var disabled = service.changeStatus(actor, created.id(), AdminAccountStatus.DISABLED,
                reviewer.version(), "staff leave", "status-1", "127.0.0.1", "test");
        assertThat(disabled.status()).isEqualTo("DISABLED");
        var enabled = service.changeStatus(actor, created.id(), AdminAccountStatus.ACTIVE,
                disabled.version(), "staff return", "status-2", "127.0.0.1", "test");
        assertThat(enabled.status()).isEqualTo("ACTIVE");
        assertThat(audit.findAll()).extracting("action").containsExactlyInAnyOrder(
                "ADMIN_ACCOUNT_CREATED", "ADMIN_ACCOUNT_ROLE_CHANGED",
                "ADMIN_ACCOUNT_STATUS_CHANGED", "ADMIN_ACCOUNT_STATUS_CHANGED");
    }

    @Test
    void deniesNonSuperAdminsAndProtectsSuperAdminAccounts() {
        AdminAccount root = accounts.saveAndFlush(AdminAccount.active("root@example.com", "hash",
                "Root", AdminRole.SUPER_ADMIN));
        AdminPrincipal actor = principal(root.getId(), AdminRole.SUPER_ADMIN);
        assertThatThrownBy(() -> service.list(principal(root.getId(), AdminRole.ADMIN),
                null, null, null, 0, 20))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> service.create(actor, "extra-root@example.com", "Root 2",
                AdminRole.SUPER_ADMIN, "long-test-password-123", MFA_SECRET,
                "create-2", null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> service.changeStatus(actor, root.getId(),
                AdminAccountStatus.DISABLED, root.getVersion(), "self disable",
                "status-3", null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThat(audit.count()).isZero();
    }

    private static AdminPrincipal principal(Long id, AdminRole role) {
        return new AdminPrincipal(id, "root@example.com", "Root", role, Set.of(),
                "session", "csrf");
    }

    @TestConfiguration
    static class Dependencies {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }

        @Bean AdminSecurityProperties securityProperties() {
            AdminSecurityProperties properties = new AdminSecurityProperties();
            properties.setMfaEncryptionKey(java.util.Base64.getEncoder()
                    .encodeToString(new byte[32]));
            return properties;
        }

        @Bean AdminMfaSecretCipher cipher(AdminSecurityProperties properties) {
            return new AdminMfaSecretCipher(properties);
        }
    }
}
