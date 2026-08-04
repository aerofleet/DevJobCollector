package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.SaraminCollectionProperties;
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
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SaraminAdapter implements JobSourceAdapter {

    private static final String BASE_URL = "https://oapi.saramin.co.kr/job-search";
    private static final String SCHEMA_VERSION = "saramin-job-search-v1-2026-08";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SaraminCollectionProperties properties;

    @Override
    public SourceType sourceType() {
        return SourceType.SARAMIN;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);
        if (!properties.enabled() || properties.accessKey().isBlank()) {
            log.warn("Saramin collection skipped: adapter is disabled or access key is missing");
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }

        List<JobRawDto> collected = new ArrayList<>();
        StringBuilder responsePayload = new StringBuilder();
        long fetchedCount = 0;
        long total = Long.MAX_VALUE;
        boolean completed = false;

        try {
            for (int page = 0; page < properties.maxPages(); page++) {
                URI uri = buildUri(target.getSourceIdentifier(), page);
                RequestEntity<Void> request = RequestEntity.get(uri)
                        .accept(MediaType.APPLICATION_JSON)
                        .build();
                ResponseEntity<String> httpResponse = restTemplate.exchange(request, String.class);
                String rawResponse = httpResponse.getBody();
                if (rawResponse == null || rawResponse.isBlank()) {
                    throw new JsonProcessingException("Saramin returned an empty response") { };
                }
                responsePayload.append(rawResponse);

                SaraminJobResponse response = objectMapper.readValue(rawResponse, SaraminJobResponse.class);
                if (response.result() != null && response.result().code() != null) {
                    return apiFailure(response.result().code(), collected, responsePayload);
                }

                SaraminJobResponse.Jobs responseJobs = response.jobs();
                if (responseJobs == null) {
                    throw new JsonProcessingException("Saramin response does not contain jobs") { };
                }
                List<SaraminJobResponse.Job> sourceJobs = responseJobs.job() == null
                        ? List.of() : responseJobs.job();
                fetchedCount += sourceJobs.size();
                total = responseJobs.total() == null ? fetchedCount : responseJobs.total();
                sourceJobs.stream()
                        .filter(job -> job.active() == null || job.active() == 1)
                        .map(this::toRawJob)
                        .forEach(collected::add);

                if (sourceJobs.isEmpty() || sourceJobs.size() < properties.pageSize() || fetchedCount >= total) {
                    completed = true;
                    break;
                }
            }

            CollectionStatus status;
            if (collected.isEmpty()) {
                status = completed ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.PARTIAL_SUCCESS;
            } else {
                status = completed ? CollectionStatus.SUCCESS : CollectionStatus.PARTIAL_SUCCESS;
            }
            return result(status, collected, completed && !collected.isEmpty(), responsePayload);
        } catch (HttpStatusCodeException e) {
            CollectionStatus failureStatus = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Saramin collection failed: target={}, status={}",
                    target.getSourceIdentifier(), e.getStatusCode().value());
            return collected.isEmpty()
                    ? CollectionResult.failed(failureStatus, SCHEMA_VERSION)
                    : result(CollectionStatus.PARTIAL_SUCCESS, collected, false, responsePayload);
        } catch (JsonProcessingException e) {
            log.warn("Saramin schema changed: target={}", target.getSourceIdentifier());
            return collected.isEmpty()
                    ? CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION)
                    : result(CollectionStatus.PARTIAL_SUCCESS, collected, false, responsePayload);
        } catch (RestClientException e) {
            log.warn("Saramin request failed: target={}", target.getSourceIdentifier());
            return collected.isEmpty()
                    ? CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION)
                    : result(CollectionStatus.PARTIAL_SUCCESS, collected, false, responsePayload);
        }
    }

    private URI buildUri(String jobMidCode, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("access-key", properties.accessKey())
                .queryParam("count", properties.pageSize())
                .queryParam("start", page)
                .queryParam("sort", "ud")
                .queryParam("fields", "posting-date,expiration-date");
        if (jobMidCode != null && !jobMidCode.isBlank()) {
            builder.queryParam("job_mid_cd", jobMidCode);
        }
        return builder.build().encode().toUri();
    }

    private CollectionResult apiFailure(
            String code,
            List<JobRawDto> collected,
            StringBuilder responsePayload
    ) {
        CollectionStatus status = "4".equals(code)
                ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
        log.warn("Saramin API rejected collection: code={}", code);
        return collected.isEmpty()
                ? CollectionResult.failed(status, SCHEMA_VERSION)
                : result(CollectionStatus.PARTIAL_SUCCESS, collected, false, responsePayload);
    }

    private CollectionResult result(
            CollectionStatus status,
            List<JobRawDto> jobs,
            boolean closureEvaluationAllowed,
            StringBuilder responsePayload
    ) {
        String payload = responsePayload.toString();
        return new CollectionResult(status, jobs, jobs.size(), SCHEMA_VERSION,
                closureEvaluationAllowed, payload.isEmpty() ? null : ContentHash.sha256(payload));
    }

    private JobRawDto toRawJob(SaraminJobResponse.Job job) {
        String rawPayload = writePayload(job);
        SaraminJobResponse.Position position = job.position();
        String companyName = job.company() == null || job.company().detail() == null
                ? null : job.company().detail().name();
        return new JobRawDto(
                sourceType(),
                job.id(),
                companyName,
                position == null ? null : position.title(),
                name(position == null ? null : position.location()),
                name(position == null ? null : position.jobType()),
                name(position == null ? null : position.experienceLevel()),
                name(position == null ? null : position.jobMidCode()),
                job.url(),
                job.url(),
                null,
                instant(job.postingTimestamp()),
                instant(job.modificationTimestamp()),
                deadline(job),
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    private String writePayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Saramin job", e);
        }
    }

    private static String name(SaraminJobResponse.NamedValue value) {
        return value == null ? null : value.name();
    }

    private static Instant instant(Long epochSeconds) {
        return epochSeconds == null || epochSeconds <= 0 ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private static Instant deadline(SaraminJobResponse.Job job) {
        return job.closeType() != null && "1".equals(job.closeType().code())
                ? instant(job.expirationTimestamp()) : null;
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Saramin target is required");
        }
    }
}
