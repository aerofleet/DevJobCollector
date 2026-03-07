package kr.itsdev.devjobcollector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "perf.log")
public record PerfLogProperties(
        long requestThresholdMs,
        long serviceThresholdMs,
        long repositoryThresholdMs
) {
    public PerfLogProperties {
        if (requestThresholdMs <= 0) {
            requestThresholdMs = 80;
        }
        if (serviceThresholdMs <= 0) {
            serviceThresholdMs = 60;
        }
        if (repositoryThresholdMs <= 0) {
            repositoryThresholdMs = 40;
        }
    }
}
