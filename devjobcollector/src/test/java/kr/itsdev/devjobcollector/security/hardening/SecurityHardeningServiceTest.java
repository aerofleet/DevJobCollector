package kr.itsdev.devjobcollector.security.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityHardeningServiceTest {
    private SecurityAuditEventRepository auditRepository;
    private SecurityHardeningProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private SecurityHardeningService service;

    @BeforeEach
    void setUp() {
        auditRepository = mock(SecurityAuditEventRepository.class);
        properties = new SecurityHardeningProperties();
        meterRegistry = new SimpleMeterRegistry();
        when(auditRepository.saveAndFlush(any(SecurityAuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = serviceAt("2026-09-10T00:00:00Z");
    }

    @Test
    void rejectsActionAfterConfiguredLimitAndPublishesBoundedMetrics() {
        properties.setCompanySignupsPerWindow(2);

        service.checkRateLimit(SecurityAction.COMPANY_SIGNUP, "actor:10");
        service.checkRateLimit(SecurityAction.COMPANY_SIGNUP, "actor:10");

        assertThatThrownBy(() -> service.checkRateLimit(
                SecurityAction.COMPANY_SIGNUP, "actor:10"))
                .isInstanceOf(SecurityRateLimitException.class)
                .hasMessage(SecurityRateLimitException.ERROR_CODE);
        assertThat(meterRegistry.get("djc.security.rate_limit.decisions")
                .tag("action", "COMPANY_SIGNUP").tag("outcome", "allowed")
                .counter().count()).isEqualTo(2);
        assertThat(meterRegistry.get("djc.security.rate_limit.decisions")
                .tag("action", "COMPANY_SIGNUP").tag("outcome", "rejected")
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTag("actor")).isNull());
    }

    @Test
    void startsFreshWindowAfterConfiguredDuration() {
        properties.setVerificationRequestsPerWindow(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-09-10T00:00:00Z"));
        service = new SecurityHardeningService(auditRepository, properties, meterRegistry, clock);
        service.checkRateLimit(SecurityAction.COMPANY_VERIFICATION_REQUEST, "company:1");

        clock.advance(Duration.ofHours(1).plusMillis(1));

        service.checkRateLimit(SecurityAction.COMPANY_VERIFICATION_REQUEST, "company:1");
        assertThat(meterRegistry.get("djc.security.rate_limit.decisions")
                .tag("action", "COMPANY_VERIFICATION_REQUEST").tag("outcome", "allowed")
                .counter().count()).isEqualTo(2);
    }

    @Test
    void persistsIdOnlyAuditEventAndIncrementsMetric() {
        service.audit(SecurityAuditEventType.COMPANY_MEMBER_ROLE_CHANGED,
                10L, 20L, 30L, "VIEWER", "RECRUITER");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(auditRepository).saveAndFlush(captor.capture());
        SecurityAuditEvent event = captor.getValue();
        assertThat(event.getEventType())
                .isEqualTo(SecurityAuditEventType.COMPANY_MEMBER_ROLE_CHANGED);
        assertThat(event.getActorUserId()).isEqualTo(10L);
        assertThat(event.getSubjectUserId()).isEqualTo(20L);
        assertThat(event.getCompanyId()).isEqualTo(30L);
        assertThat(event.getPreviousValue()).isEqualTo("VIEWER");
        assertThat(event.getNewValue()).isEqualTo("RECRUITER");
        assertThat(event.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 0, 0));
        assertThat(event.toString()).doesNotContain("email", "business", "evidence", "token");
        assertThat(meterRegistry.get("djc.security.audit.events")
                .tag("action", "COMPANY_MEMBER_ROLE_CHANGED").tag("outcome", "persisted")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void storesOnlyDigestsForRateLimitDimensions() {
        String sensitiveDimension = "business:123-45-67890";

        service.checkRateLimit(SecurityAction.COMPANY_SIGNUP, sensitiveDimension);

        Object attempts = ReflectionTestUtils.getField(service, "attempts");
        assertThat(attempts).asString()
                .doesNotContain("123-45-67890", "1234567890")
                .contains("COMPANY_SIGNUP");
    }

    private SecurityHardeningService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new SecurityHardeningService(auditRepository, properties, meterRegistry, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
