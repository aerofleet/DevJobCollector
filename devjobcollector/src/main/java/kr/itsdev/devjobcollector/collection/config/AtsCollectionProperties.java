package kr.itsdev.devjobcollector.collection.config;

import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "collection.ats")
public record AtsCollectionProperties(
        boolean enabled,
        int closureMissThreshold,
        List<Target> targets
) {
    public AtsCollectionProperties {
        closureMissThreshold = closureMissThreshold < 1 ? 2 : closureMissThreshold;
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public record Target(
            boolean enabled,
            String companyName,
            SourceType provider,
            String sourceIdentifier,
            String careersUrl,
            CollectionTier collectionTier
    ) {
    }
}
