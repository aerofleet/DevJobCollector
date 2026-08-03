package kr.itsdev.devjobcollector.collection.service;

import kr.itsdev.devjobcollector.collection.config.AtsCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.TargetStatus;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AtsCollectionScheduler {

    private static final EnumSet<TargetStatus> COLLECTABLE_STATUSES =
            EnumSet.of(TargetStatus.ACTIVE, TargetStatus.VERIFYING, TargetStatus.DEGRADED);

    private final AtsCollectionProperties properties;
    private final CompanySourceTargetRepository targetRepository;
    private final CollectionOrchestrator orchestrator;

    @Scheduled(
            initialDelayString = "${collection.ats.initial-delay-ms:30000}",
            fixedDelayString = "${collection.ats.poll-delay-ms:600000}")
    public void collectDueTargets() {
        if (!properties.enabled()) {
            return;
        }

        List<CompanySourceTarget> dueTargets =
                targetRepository.findDueTargets(COLLECTABLE_STATUSES, Instant.now());
        if (dueTargets.isEmpty()) {
            log.debug("No ATS collection targets are due");
            return;
        }

        log.info("ATS collection started: dueTargets={}", dueTargets.size());
        for (CompanySourceTarget target : dueTargets) {
            collect(target);
        }
    }

    private void collect(CompanySourceTarget target) {
        try {
            CollectionResult result = orchestrator.collectTarget(target.getId());
            log.info("ATS collection completed: provider={}, identifier={}, status={}, received={}",
                    target.getProvider(), target.getSourceIdentifier(), result.status(), result.receivedCount());
        } catch (RuntimeException e) {
            log.error("ATS collection crashed: provider={}, identifier={}",
                    target.getProvider(), target.getSourceIdentifier(), e);
        }
    }
}
