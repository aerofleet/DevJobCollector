package kr.itsdev.devjobcollector.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collection.saramin")
public record SaraminCollectionProperties(
        boolean enabled,
        String accessKey,
        int pageSize,
        int maxPages
) {
    public SaraminCollectionProperties {
        accessKey = accessKey == null ? "" : accessKey.trim();
        pageSize = pageSize < 1 ? 110 : Math.min(pageSize, 110);
        maxPages = maxPages < 1 ? 10 : maxPages;
    }
}
