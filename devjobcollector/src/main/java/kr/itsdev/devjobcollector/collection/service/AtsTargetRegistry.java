package kr.itsdev.devjobcollector.collection.service;

import kr.itsdev.devjobcollector.collection.config.AtsCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.TargetStatus;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AtsTargetRegistry implements ApplicationRunner {

    private final AtsCollectionProperties properties;
    private final CompanySourceTargetRepository targetRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("ATS collection is disabled");
            return;
        }

        properties.targets().stream()
                .filter(AtsCollectionProperties.Target::enabled)
                .forEach(this::register);
    }

    private void register(AtsCollectionProperties.Target configured) {
        if (configured.provider() == null || configured.sourceIdentifier() == null
                || configured.sourceIdentifier().isBlank()) {
            log.warn("Skipping invalid ATS target configuration: {}", configured);
            return;
        }

        CompanySourceTarget target = targetRepository
                .findByProviderAndSourceIdentifier(configured.provider(), configured.sourceIdentifier().trim())
                .orElseGet(() -> new CompanySourceTarget(
                        null,
                        configured.companyName(),
                        configured.provider(),
                        configured.sourceIdentifier(),
                        configured.careersUrl(),
                        tier(configured)));

        target.updateMetadata(configured.companyName(), configured.careersUrl(), tier(configured));
        if (target.getStatus() == TargetStatus.DISCOVERED || target.getStatus() == TargetStatus.VERIFYING) {
            target.activate();
        }
        targetRepository.save(target);
        log.info("ATS target registered: provider={}, identifier={}, company={}, status={}",
                target.getProvider(), target.getSourceIdentifier(), target.displayCompanyName(), target.getStatus());
    }

    private static CollectionTier tier(AtsCollectionProperties.Target configured) {
        return configured.collectionTier() == null ? CollectionTier.A : configured.collectionTier();
    }
}
