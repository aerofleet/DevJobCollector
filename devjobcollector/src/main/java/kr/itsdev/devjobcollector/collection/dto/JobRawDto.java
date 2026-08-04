package kr.itsdev.devjobcollector.collection.dto;

import kr.itsdev.devjobcollector.collection.domain.SourceType;

import java.time.Instant;

public record JobRawDto(
        SourceType provider,
        String sourceJobId,
        String companyName,
        String title,
        String location,
        String employmentType,
        String experience,
        String team,
        String sourceUrl,
        String applyUrl,
        String plainDescription,
        Instant publishedAt,
        Instant updatedAtSource,
        Instant deadlineAt,
        String contentHash,
        String rawPayload
) {
    public JobRawDto(
            SourceType provider,
            String sourceJobId,
            String title,
            String location,
            String employmentType,
            String team,
            String sourceUrl,
            String applyUrl,
            String plainDescription,
            Instant publishedAt,
            Instant updatedAtSource,
            String contentHash,
            String rawPayload
    ) {
        this(provider, sourceJobId, null, title, location, employmentType, null, team,
                sourceUrl, applyUrl, plainDescription, publishedAt, updatedAtSource, null,
                contentHash, rawPayload);
    }
}
