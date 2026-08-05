package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.CompanyPageCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossCareerAdapter implements JobSourceAdapter {

    static final String JOB_GROUPS_URL =
            "https://api-public.toss.im/api/v3/ipd-eggnog/career/job-groups";
    private static final String DETAIL_URL = "https://toss.im/career/job-detail?job_id=";
    private static final String SCHEMA_VERSION = "toss-career-job-groups-v1-2026-08";
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern CLOSING_TIME = Pattern.compile("(\\d{1,2})(?::|시)?(\\d{2})?");

    private static final long EMPLOYMENT_TYPE_ID = 4_112_432_003L;
    private static final long AFFILIATE_ID = 4_169_410_003L;
    private static final long JOB_CATEGORY_ID = 24_623_243_003L;
    private static final long DESCRIPTION_ID = 4_155_730_003L;
    private static final long HIDDEN_FOR_LIST_ID = 5_038_345_003L;
    private static final long CLOSING_DATE_ID = 11_431_213_003L;
    private static final long CLOSING_TIME_ID = 20_500_786_003L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CompanyPageCollectionProperties properties;

    @Override
    public SourceType sourceType() {
        return SourceType.TOSS_CAREERS;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);
        try {
            RequestEntity<Void> request = RequestEntity.get(URI.create(JOB_GROUPS_URL))
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .accept(MediaType.APPLICATION_JSON)
                    .build();
            ResponseEntity<String> responseEntity = restTemplate.exchange(request, String.class);
            String rawResponse = responseEntity.getBody();
            if (rawResponse == null || rawResponse.isBlank()) {
                return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
            }

            TossCareerResponse response = objectMapper.readValue(rawResponse, TossCareerResponse.class);
            if (response.success() == null) {
                return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
            }
            List<JobRawDto> jobs = response.success().stream()
                    .filter(Objects::nonNull)
                    .filter(this::isPublicOpening)
                    .map(group -> toRawJob(target, group))
                    .toList();
            CollectionStatus status = jobs.isEmpty()
                    ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS;
            return new CollectionResult(status, jobs, jobs.size(), SCHEMA_VERSION,
                    !jobs.isEmpty(), ContentHash.sha256(rawResponse));
        } catch (HttpStatusCodeException e) {
            CollectionStatus status = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Toss career collection failed: target={}, status={}",
                    target.getSourceIdentifier(), e.getStatusCode().value());
            return CollectionResult.failed(status, SCHEMA_VERSION);
        } catch (JsonProcessingException e) {
            log.warn("Toss career schema changed: target={}", target.getSourceIdentifier(), e);
            return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
        } catch (RestClientException e) {
            log.warn("Toss career request failed: target={}", target.getSourceIdentifier(), e);
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }
    }

    private boolean isPublicOpening(TossCareerResponse.JobGroup group) {
        TossCareerResponse.Job job = group.primaryJob();
        return group.id() != null && job != null && job.internalJobId() != null
                && !metadataBoolean(job, HIDDEN_FOR_LIST_ID);
    }

    private JobRawDto toRawJob(CompanySourceTarget target, TossCareerResponse.JobGroup group) {
        TossCareerResponse.Job job = group.primaryJob();
        String rawPayload = writePayload(group);
        String sourceUrl = DETAIL_URL + group.id();
        String description = truncate(metadataText(job, DESCRIPTION_ID));
        String affiliate = metadataText(job, AFFILIATE_ID);
        return new JobRawDto(
                sourceType(),
                String.valueOf(group.id()),
                firstNonBlank(affiliate, target.displayCompanyName()),
                firstNonBlank(group.title(), job.title()),
                job.location() == null ? null : job.location().name(),
                metadataText(job, EMPLOYMENT_TYPE_ID),
                null,
                metadataText(job, JOB_CATEGORY_ID),
                sourceUrl,
                firstNonBlank(job.absoluteUrl(), sourceUrl),
                description,
                parseInstant(job.firstPublished()),
                parseInstant(job.updatedAt()),
                parseDeadline(job),
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    private Instant parseDeadline(TossCareerResponse.Job job) {
        Instant applicationDeadline = parseInstant(job.applicationDeadline());
        if (applicationDeadline != null) {
            return applicationDeadline;
        }
        String dateValue = metadataText(job, CLOSING_DATE_ID);
        if (dateValue == null) {
            return null;
        }
        try {
            LocalDate closingDate = LocalDate.parse(dateValue.trim().substring(0, 10));
            LocalTime closingTime = parseClosingTime(metadataText(job, CLOSING_TIME_ID));
            return closingDate.atTime(closingTime).atZone(SERVICE_ZONE).toInstant();
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static LocalTime parseClosingTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.MAX;
        }
        Matcher matcher = CLOSING_TIME.matcher(value.trim());
        if (!matcher.find()) {
            return LocalTime.MAX;
        }
        try {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException | DateTimeException e) {
            return LocalTime.MAX;
        }
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

    private static String metadataText(TossCareerResponse.Job job, long id) {
        if (job.metadata() == null) {
            return null;
        }
        return job.metadata().stream()
                .filter(Objects::nonNull)
                .filter(metadata -> metadata.id() != null && metadata.id() == id)
                .map(TossCareerResponse.Metadata::value)
                .filter(Objects::nonNull)
                .filter(JsonNode::isValueNode)
                .map(JsonNode::asText)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static boolean metadataBoolean(TossCareerResponse.Job job, long id) {
        if (job.metadata() == null) {
            return false;
        }
        return job.metadata().stream()
                .filter(Objects::nonNull)
                .filter(metadata -> metadata.id() != null && metadata.id() == id)
                .map(TossCareerResponse.Metadata::value)
                .filter(Objects::nonNull)
                .anyMatch(value -> value.isBoolean() ? value.booleanValue()
                        : Boolean.parseBoolean(value.asText()));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= properties.maxDescriptionLength()) {
            return value;
        }
        return value.substring(0, properties.maxDescriptionLength());
    }

    private String writePayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Toss career job", e);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Toss career target is required");
        }
    }
}
