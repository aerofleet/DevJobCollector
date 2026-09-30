package kr.itsdev.devjobcollector.admin.companies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.company.CompanyEvidenceService;
import kr.itsdev.devjobcollector.company.CompanyVerificationService;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
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

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, AdminCompanyService.class, AdminCompanyReviewService.class,
        CompanyEvidenceService.class})
class AdminCompanyReviewIntegrationTest {
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

    @Test
    void approvalPersistsReviewerAndAuditAtomically() {
        var detail = service.review(9944L, 9944L, true, null, principal(),
                "review-request", "127.0.0.1", "integration-test");
        assertThat(detail.company().status()).isEqualTo("VERIFIED");
        assertThat(detail.latestRequest().status()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT reviewed_by_admin FROM company_verification_requests WHERE id = 9944",
                Long.class)).isEqualTo(9944L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs "
                + "WHERE action = 'COMPANY_VERIFICATION_APPROVED' AND target_id = '9944'", Long.class))
                .isEqualTo(1L);
    }

    private AdminPrincipal principal() {
        return new AdminPrincipal(9944L, "review-admin@example.com", "Reviewer", AdminRole.REVIEWER,
                Set.of(), "session", "a".repeat(64));
    }
}
