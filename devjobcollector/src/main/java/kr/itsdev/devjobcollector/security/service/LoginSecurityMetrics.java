package kr.itsdev.devjobcollector.security.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LoginSecurityMetrics {
    enum Outcome { SUCCESS, FAILURE, LOCK_CREATED, LOCK_REJECTED, UNKNOWN }
    private final MeterRegistry registry;

    public LoginSecurityMetrics(MeterRegistry registry) { this.registry = registry; }

    void record(Outcome outcome) {
        Runnable update = () -> registry.counter("djc.member.login", "outcome", outcome.name()).increment();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            update.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { update.run(); }
        });
    }
}
