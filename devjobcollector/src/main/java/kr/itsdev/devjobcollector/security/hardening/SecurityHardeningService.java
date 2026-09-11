package kr.itsdev.devjobcollector.security.hardening;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SecurityHardeningService {
    private final SecurityAuditEventRepository auditRepository;
    private final SecurityHardeningProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Map<LimitKey, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public SecurityHardeningService(SecurityAuditEventRepository auditRepository,
                                    SecurityHardeningProperties properties,
                                    MeterRegistry meterRegistry) {
        this(auditRepository, properties, meterRegistry, Clock.systemDefaultZone());
    }

    SecurityHardeningService(SecurityAuditEventRepository auditRepository,
                             SecurityHardeningProperties properties,
                             MeterRegistry meterRegistry, Clock clock) {
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public void checkRateLimit(SecurityAction action, String... dimensions) {
        Objects.requireNonNull(action, "action is required");
        if (dimensions == null || dimensions.length == 0) {
            throw new IllegalArgumentException("at least one rate-limit dimension is required");
        }
        for (String dimension : dimensions) {
            checkKey(action, digest(dimension));
        }
        increment("djc.security.rate_limit.decisions", action.name(), "allowed");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SecurityAuditEvent audit(SecurityAuditEventType eventType, Long actorUserId,
                                    Long subjectUserId, Long companyId,
                                    String previousValue, String newValue) {
        SecurityAuditEvent event = auditRepository.saveAndFlush(SecurityAuditEvent.occurred(
                eventType, actorUserId, subjectUserId, companyId, previousValue, newValue,
                LocalDateTime.now(clock)));
        incrementAfterCommit("djc.security.audit.events", eventType.name(), "persisted");
        return event;
    }

    private void checkKey(SecurityAction action, String digest) {
        Instant now = clock.instant();
        cleanupIfNecessary(now);
        LimitKey key = new LimitKey(action, digest);
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            removeExpired(timestamps, now);
            if (timestamps.size() >= properties.limitFor(action)) {
                increment("djc.security.rate_limit.decisions", action.name(), "rejected");
                throw new SecurityRateLimitException();
            }
            timestamps.addLast(now);
        }
    }

    private void cleanupIfNecessary(Instant now) {
        if (attempts.size() < properties.getMaxTrackedKeys()) {
            return;
        }
        attempts.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                removeExpired(timestamps, now);
                return timestamps.isEmpty();
            }
        });
        if (attempts.size() >= properties.getMaxTrackedKeys()) {
            increment("djc.security.rate_limit.capacity", "all", "rejected");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SECURITY_RATE_LIMIT_CAPACITY_EXCEEDED");
        }
    }

    private void removeExpired(Deque<Instant> timestamps, Instant now) {
        Instant cutoff = now.minus(Duration.ofMinutes(properties.getWindowMinutes()));
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.removeFirst();
        }
    }

    private void increment(String metricName, String action, String outcome) {
        Counter.builder(metricName)
                .tag("action", action)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void incrementAfterCommit(String metricName, String action, String outcome) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            increment(metricName, action, outcome);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                increment(metricName, action, outcome);
            }
        });
    }

    private String digest(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record LimitKey(SecurityAction action, String digest) {
    }
}
