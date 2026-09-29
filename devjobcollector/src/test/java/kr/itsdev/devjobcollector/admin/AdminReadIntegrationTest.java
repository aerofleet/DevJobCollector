package kr.itsdev.devjobcollector.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.audit.AdminAuditService;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.companies.AdminCompanyService;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, AdminCompanyService.class, AdminAuditService.class})
class AdminReadIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminCompanyService companies;
    @Autowired AdminAuditService audit;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @Test
    void companyReadOmitsOpaqueEvidenceKey() throws Exception {
        jdbc.update("INSERT INTO users (id,email,name,role,status,provider,version) "
                + "VALUES (9901,'company-reader@example.com','Owner','USER','ACTIVE','LOCAL',0)");
        jdbc.update("INSERT INTO companies (id,legal_name,display_name,business_number_hash,"
                + "business_number_masked,status,created_by,version) VALUES "
                + "(9901,'Read Legal','Read Company',?,'123-**-*****','PENDING_VERIFICATION',9901,0)",
                "a".repeat(64));
        jdbc.update("INSERT INTO company_verification_requests "
                + "(company_id,requested_by,method,status,evidence_object_key,requested_at) "
                + "VALUES (9901,9901,'BUSINESS_REGISTRATION_DOCUMENT','PENDING',"
                + "'private/evidence-secret.pdf',CURRENT_TIMESTAMP(6))");

        assertThat(companies.list("Read Company", CompanyStatus.PENDING_VERIFICATION, 0, 20)
                .getTotalElements()).isEqualTo(1);
        var detail = companies.detail(9901L);
        assertThat(detail.latestRequest().status()).isEqualTo("PENDING");
        assertThat(mapper.writeValueAsString(detail)).doesNotContain("evidence-secret", "evidenceObjectKey");
    }

    @Test
    void auditSearchIsRestrictedAndOmitsNetworkAndJsonFields() throws Exception {
        jdbc.update("INSERT INTO admin_accounts (id,email,password_hash,name,role,status) "
                + "VALUES (9901,'audit-reader@example.com',?,'Root','SUPER_ADMIN','ACTIVE')",
                "p".repeat(60));
        jdbc.update("INSERT INTO admin_audit_logs (actor_admin_id,action,target_type,target_id,"
                + "reason,before_json,after_json,result,request_id,ip_address) "
                + "VALUES (9901,'USER_STATUS_CHANGED','USER','9901','policy',"
                + "'{\"secret\":\"before\"}','{\"secret\":\"after\"}',"
                + "'SUCCESS','audit-read-test','192.0.2.1')");
        var reviewer = principal(AdminRole.REVIEWER);
        assertThatThrownBy(() -> audit.search(reviewer, null, null, null, null,
                null, null, null, 0, 20)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        var result = audit.search(principal(AdminRole.SUPER_ADMIN), 9901L,
                "USER_STATUS_CHANGED", "USER", "9901", AdminAuditResult.SUCCESS,
                null, null, 0, 20);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(mapper.writeValueAsString(result.getContent()))
                .contains("audit-read-test").doesNotContain("192.0.2.1", "secret");
    }

    private static AdminPrincipal principal(AdminRole role) {
        return new AdminPrincipal(9901L, "audit-reader@example.com", "Root", role,
                Set.of(), "session", "csrf");
    }
}
