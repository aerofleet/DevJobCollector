package kr.itsdev.devjobcollector.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collection.company-page")
public record CompanyPageCollectionProperties(
        String userAgent,
        int maxDescriptionLength
) {
    public CompanyPageCollectionProperties {
        userAgent = userAgent == null || userAgent.isBlank()
                ? "DevJobCollector/1.0 (+https://itsdev.kr)"
                : userAgent.trim();
        maxDescriptionLength = maxDescriptionLength < 1 ? 20_000 : maxDescriptionLength;
    }
}
