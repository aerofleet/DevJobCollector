package kr.itsdev.devjobcollector.security.hardening;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, SecurityHardeningService.class, SecurityHardeningProperties.class,
        SecurityHardeningIntegrationTest.MetricsConfig.class})
class SecurityHardeningIntegrationTest {
    @Autowired SecurityHardeningService service;
    @Autowired SecurityAuditEventRepository repository;
    @Autowired MeterRegistry meterRegistry;
    @Autowired PlatformTransactionManager transactionManager;

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
    void persistsEveryEnterpriseAuditTypeWithoutSensitivePayloadColumns() {
        long companyId = 700L;
        Arrays.stream(SecurityAuditEventType.values()).forEach(eventType ->
                service.audit(eventType, 10L, 20L, companyId, "BEFORE", "AFTER"));
        repository.flush();

        var events = repository.findAllByCompanyIdOrderByOccurredAtAscIdAsc(companyId);
        assertThat(events).extracting(SecurityAuditEvent::getEventType)
                .containsExactlyElementsOf(Arrays.asList(SecurityAuditEventType.values()));
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getActorUserId()).isEqualTo(10L);
            assertThat(event.getSubjectUserId()).isEqualTo(20L);
            assertThat(event.getPreviousValue()).isEqualTo("BEFORE");
            assertThat(event.getNewValue()).isEqualTo("AFTER");
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void publishesAuditMetricOnlyAfterDatabaseCommit() {
        repository.deleteAll();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(ignored -> service.audit(
                SecurityAuditEventType.COMPANY_CREATED,
                10L, 10L, 700L, null, "PENDING_VERIFICATION"));
        double committedCount = meterRegistry.get("djc.security.audit.events")
                .tag("action", "COMPANY_CREATED").tag("outcome", "persisted")
                .counter().count();

        transaction.executeWithoutResult(status -> {
            service.audit(SecurityAuditEventType.COMPANY_CREATED,
                    10L, 10L, 701L, null, "PENDING_VERIFICATION");
            status.setRollbackOnly();
        });

        assertThat(repository.count()).isEqualTo(1);
        assertThat(committedCount).isEqualTo(1);
        assertThat(meterRegistry.get("djc.security.audit.events")
                .tag("action", "COMPANY_CREATED").tag("outcome", "persisted")
                .counter().count()).isEqualTo(1);
    }

    @TestConfiguration
    static class MetricsConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
