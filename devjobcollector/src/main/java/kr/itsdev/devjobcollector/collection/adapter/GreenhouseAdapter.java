package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class GreenhouseAdapter implements JobSourceAdapter {

    private static final String BASE_URL = "https://boards-api.greenhouse.io/v1/boards";
    private static final String SCHEMA_VERSION = "greenhouse-job-board-v1-2026-08";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SourceType sourceType() {
        return SourceType.GREENHOUSE;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);
        String url = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment(target.getSourceIdentifier(), "jobs")
                .queryParam("content", true)
                .build()
                .toUriString();

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            GreenhouseJobResponse response = objectMapper.readValue(rawResponse, GreenhouseJobResponse.class);
            List<GreenhouseJobResponse.Job> sourceJobs = response.jobs() == null ? List.of() : response.jobs();
            List<JobRawDto> jobs = sourceJobs.stream().map(this::toRawJob).toList();
            CollectionStatus status = jobs.isEmpty() ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS;
            return new CollectionResult(status, jobs, jobs.size(), SCHEMA_VERSION,
                    !jobs.isEmpty(), ContentHash.sha256(rawResponse));
        } catch (HttpStatusCodeException e) {
            CollectionStatus status = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Greenhouse collection failed: target={}, status={}",
                    target.getSourceIdentifier(), e.getStatusCode().value());
            return CollectionResult.failed(status, SCHEMA_VERSION);
        } catch (JsonProcessingException e) {
            log.warn("Greenhouse schema changed: target={}", target.getSourceIdentifier(), e);
            return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
        } catch (RestClientException e) {
            log.warn("Greenhouse request failed: target={}", target.getSourceIdentifier(), e);
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }
    }

    private JobRawDto toRawJob(GreenhouseJobResponse.Job job) {
        String rawPayload = writePayload(job);
        String team = job.departments() == null ? null : job.departments().stream()
                .map(GreenhouseJobResponse.Department::name)
                .filter(Objects::nonNull)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        return new JobRawDto(
                sourceType(),
                String.valueOf(job.id()),
                job.title(),
                job.location() == null ? null : job.location().name(),
                null,
                team,
                job.absoluteUrl(),
                job.absoluteUrl(),
                toPlainText(job.content()),
                parseInstant(job.firstPublished()),
                parseInstant(job.updatedAt()),
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    private String writePayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Greenhouse job", e);
        }
    }

    private static String toPlainText(String html) {
        if (html == null) {
            return null;
        }
        return HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Greenhouse target is required");
        }
    }
}
