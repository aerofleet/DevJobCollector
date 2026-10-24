package kr.itsdev.devjobcollector.admin.companies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, AdminCompanyService.class, AdminCompanyReviewService.class,
        CompanyEvidenceService.class})
class AdminCompanyReviewIntegrationTest {
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminCompanyReviewService service;
    @MockitoBean CompanyVerificationService verificationService;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @BeforeEach
    void fixture() {
        jdbc.update("INSERT INTO users (id,email,name,role,status,provider,version) "
                + "VALUES (9944,'review-owner@example.com','Owner','USER','ACTIVE','LOCAL',0)");
        jdbc.update("INSERT INTO admin_accounts (id,email,password_hash,name,role,status) "
                + "VALUES (9944,'review-admin@example.com',?,'Reviewer','REVIEWER','ACTIVE')",
                "p".repeat(60));
        jdbc.update("INSERT INTO companies (id,legal_name,display_name,business_number_hash,"
                + "business_number_masked,status,created_by,version) VALUES "
                + "(9944,'Review Legal','Review Company',?,'123-**-*****','PENDING_VERIFICATION',9944,0)",
                "b".repeat(64));
        jdbc.update("INSERT INTO company_verification_requests "
                + "(id,company_id,requested_by,method,status,evidence_object_key,requested_at) "
                + "VALUES (9944,9944,9944,'BUSINESS_REGISTRATION_DOCUMENT','PENDING',"
                + "'company-verification/test/evidence',CURRENT_TIMESTAMP(6))");
        jdbc.update("INSERT INTO company_verification_evidence (request_id,content_type,content) "
                + "VALUES (9944,'application/pdf',?)",
                "%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM admin_idempotency_keys WHERE admin_id = 9944");
        jdbc.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = 9944");
        jdbc.update("DELETE FROM company_verification_evidence WHERE request_id = 9944");
        jdbc.update("DELETE FROM company_verification_requests WHERE id = 9944");
        jdbc.update("DELETE FROM companies WHERE id = 9944");
        jdbc.update("DELETE FROM admin_accounts WHERE id = 9944");
        jdbc.update("DELETE FROM users WHERE id = 9944");
    }

    @Test
    void approvalPersistsReviewerAndAuditAtomically() {
        var detail = service.review(9944L, 9944L, true, null, KEY, principal(),
                "review-request", "127.0.0.1", "integration-test");
        var retried = service.review(9944L, 9944L, true, null, KEY, principal(),
                "review-retry", "127.0.0.1", "integration-test");
        assertThat(detail.company().status()).isEqualTo("VERIFIED");
        assertThat(retried.company().status()).isEqualTo("VERIFIED");
        assertThat(detail.latestRequest().status()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT reviewed_by_admin FROM company_verification_requests WHERE id = 9944",
                Long.class)).isEqualTo(9944L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs "
                + "WHERE action = 'COMPANY_VERIFICATION_APPROVED' AND target_id = '9944'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_idempotency_keys "
                + "WHERE admin_id = 9944 AND idempotency_key = ?", Long.class, KEY)).isEqualTo(1L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackReviewAndIdempotencyRecord() {
        assertThatThrownBy(() -> service.review(9944L, 9944L, true, null, KEY, principal(),
                null, "127.0.0.1", "integration-test"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM companies WHERE id = 9944", String.class))
                .isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbc.queryForObject("SELECT status FROM company_verification_requests WHERE id = 9944",
                String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE actor_admin_id = 9944",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_idempotency_keys WHERE admin_id = 9944",
                Long.class)).isZero();
    }

    private AdminPrincipal principal() {
        return new AdminPrincipal(9944L, "review-admin@example.com", "Reviewer", AdminRole.REVIEWER,
                Set.of(), "session", "a".repeat(64));
    }
}
