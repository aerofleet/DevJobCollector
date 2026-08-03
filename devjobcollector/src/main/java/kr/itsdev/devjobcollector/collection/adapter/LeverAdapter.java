package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeverAdapter implements JobSourceAdapter {

    private static final String BASE_URL = "https://api.lever.co/v0/postings";
    private static final String SCHEMA_VERSION = "lever-postings-v0-2026-08";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SourceType sourceType() {
        return SourceType.LEVER;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);
        String url = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment(target.getSourceIdentifier())
                .queryParam("mode", "json")
                .build()
                .toUriString();

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            List<LeverPostingResponse> sourceJobs = objectMapper.readValue(
                    rawResponse, new TypeReference<List<LeverPostingResponse>>() {});
            List<JobRawDto> jobs = sourceJobs.stream().map(this::toRawJob).toList();
            CollectionStatus status = jobs.isEmpty() ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS;
            return new CollectionResult(status, jobs, jobs.size(), SCHEMA_VERSION,
                    !jobs.isEmpty(), ContentHash.sha256(rawResponse));
        } catch (HttpStatusCodeException e) {
            CollectionStatus status = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Lever collection failed: target={}, status={}",
                    target.getSourceIdentifier(), e.getStatusCode().value());
            return CollectionResult.failed(status, SCHEMA_VERSION);
        } catch (JsonProcessingException e) {
            log.warn("Lever schema changed: target={}", target.getSourceIdentifier(), e);
            return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
        } catch (RestClientException e) {
            log.warn("Lever request failed: target={}", target.getSourceIdentifier(), e);
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }
    }

    private JobRawDto toRawJob(LeverPostingResponse job) {
        String rawPayload = writePayload(job);
        LeverPostingResponse.Categories categories = job.categories();
        return new JobRawDto(
                sourceType(),
                job.id(),
                job.text(),
                categories == null ? null : categories.location(),
                categories == null ? null : categories.commitment(),
                categories == null ? null : categories.team(),
                job.hostedUrl(),
                job.applyUrl(),
                job.descriptionPlain(),
                job.createdAt() == null ? null : Instant.ofEpochMilli(job.createdAt()),
                null,
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    private String writePayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Lever job", e);
        }
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Lever target is required");
        }
    }
}
