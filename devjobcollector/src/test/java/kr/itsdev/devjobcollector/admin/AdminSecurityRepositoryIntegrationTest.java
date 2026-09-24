package kr.itsdev.devjobcollector.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.admin.bootstrap.AdminBootstrapResult;
import kr.itsdev.devjobcollector.admin.bootstrap.AdminBootstrapService;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, AdminBootstrapService.class,
        AdminSecurityRepositoryIntegrationTest.PasswordTestConfiguration.class})
class AdminSecurityRepositoryIntegrationTest {
    @Autowired AdminAccountRepository accountRepository;
    @Autowired AdminSessionRepository sessionRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired EntityManager entityManager;
    @Autowired AdminBootstrapService bootstrapService;
    @Autowired PasswordEncoder passwordEncoder;

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
    void persistsAccountPermissionAndHashedSession() {
        AdminAccount account = newAccount("Security.Admin@example.com");
        account.grant(AdminPermission.RESUME_READ);
        account.configureMfa(new byte[] {1, 2, 3}, "admin-mfa-key-v1");
        accountRepository.saveAndFlush(account);
        LocalDateTime issuedAt = LocalDateTime.of(2026, 9, 24, 10, 0);
        AdminSession session = sessionRepository.saveAndFlush(AdminSession.issue(
                account, "a".repeat(64), "b".repeat(64), issuedAt, issuedAt.plusHours(1)));
        entityManager.clear();

        AdminAccount foundAccount = accountRepository.findByEmail("security.admin@example.com")
                .orElseThrow();
        AdminSession foundSession = sessionRepository.findByRefreshHash("a".repeat(64))
                .orElseThrow();

        assertThat(foundAccount.getPermissions()).containsExactly(AdminPermission.RESUME_READ);
        assertThat(foundAccount.getMfaSecretCiphertext()).containsExactly(1, 2, 3);
        assertThat(foundAccount.getCredentialVersion()).isEqualTo(1);
        assertThat(foundSession.getId()).isEqualTo(session.getId());
        assertThat(foundSession.isUsableAt(issuedAt.plusMinutes(30))).isTrue();
    }

    @Test
    void rejectsCaseInsensitiveDuplicateEmail() {
        accountRepository.saveAndFlush(newAccount("duplicate-admin@example.com"));

        assertThatThrownBy(() -> accountRepository.saveAndFlush(
                newAccount("DUPLICATE-ADMIN@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsStructuredAuditWithoutAccountRelationship() {
        AdminAccount account = accountRepository.saveAndFlush(newAccount("audit@example.com"));
        AdminAuditLog audit = auditLogRepository.saveAndFlush(AdminAuditLog.record(
                account.getId(), "COMPANY_APPROVE", "COMPANY", "17", "documents verified",
                "{\"status\":\"PENDING_VERIFICATION\"}",
                "{\"status\":\"VERIFIED\"}", AdminAuditResult.SUCCESS, "request-17",
                "127.0.0.1", "integration-test"));
        entityManager.clear();

        AdminAuditLog found = auditLogRepository.findById(audit.getId()).orElseThrow();

        assertThat(found.getActorAdminId()).isEqualTo(account.getId());
        assertThat(found.getBeforeJson()).contains("PENDING_VERIFICATION");
        assertThat(found.getAfterJson()).contains("VERIFIED");
        assertThat(found.getOccurredAt()).isNotNull();
    }

    @Test
    void provisionsInitialSuperAdminOnceWithHashAndAudit() {
        char[] rawPassword = "integration-bootstrap-passphrase".toCharArray();

        AdminBootstrapResult created = bootstrapService.provision(
                "Bootstrap.Admin@example.com", "초기 관리자", rawPassword);
        AdminBootstrapResult repeated = bootstrapService.provision(
                "bootstrap.admin@example.com", "초기 관리자", rawPassword);
        entityManager.flush();
        entityManager.clear();

        AdminAccount account = accountRepository.findByEmail("bootstrap.admin@example.com")
                .orElseThrow();
        assertThat(created).isEqualTo(AdminBootstrapResult.CREATED);
        assertThat(repeated).isEqualTo(AdminBootstrapResult.ALREADY_EXISTS);
        assertThat(account.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(account.getPasswordHash()).doesNotContain("integration-bootstrap-passphrase");
        assertThat(passwordEncoder.matches(
                "integration-bootstrap-passphrase", account.getPasswordHash())).isTrue();
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findAll().getFirst().getAction())
                .isEqualTo("ADMIN_BOOTSTRAP");
    }

    private AdminAccount newAccount(String email) {
        return AdminAccount.active(email, "$2a$12$" + "a".repeat(53),
                "보안 관리자", AdminRole.ADMIN);
    }

    @TestConfiguration
    static class PasswordTestConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }
}
