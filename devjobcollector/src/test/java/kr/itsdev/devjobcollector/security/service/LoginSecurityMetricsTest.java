package kr.itsdev.devjobcollector.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LoginSecurityMetricsTest {
    @Test
    void countsOnlyAfterCommitWithoutUserIdentifiers() {
        var registry = new SimpleMeterRegistry();
        var metrics = new LoginSecurityMetrics(registry);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            metrics.record(LoginSecurityMetrics.Outcome.LOCK_CREATED);
            assertThat(registry.find("djc.member.login").tag("outcome", "LOCK_CREATED").counter().count()).isZero();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            var counter = registry.find("djc.member.login").tag("outcome", "LOCK_CREATED").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1);
            assertThat(counter.getId().getTags()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void initializesEveryOutcomeAtZeroAndDoesNotCountRollback() {
        var registry = new SimpleMeterRegistry();
        var metrics = new LoginSecurityMetrics(registry);
        assertThat(registry.find("djc.member.login").counters()).hasSize(LoginSecurityMetrics.Outcome.values().length);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            metrics.record(LoginSecurityMetrics.Outcome.FAILURE);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
            assertThat(registry.find("djc.member.login").tag("outcome", "FAILURE").counter().count()).isZero();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
