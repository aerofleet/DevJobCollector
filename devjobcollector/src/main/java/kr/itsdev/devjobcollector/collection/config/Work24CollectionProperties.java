package kr.itsdev.devjobcollector.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collection.work24")
public record Work24CollectionProperties(boolean enabled, String accessKey, int pageSize, int maxPages) {
    public Work24CollectionProperties {
        accessKey = accessKey == null ? "" : accessKey.trim();
        pageSize = pageSize < 1 ? 100 : Math.min(pageSize, 100);
        maxPages = maxPages < 1 ? 10 : Math.min(maxPages, 1000);
    }
}
