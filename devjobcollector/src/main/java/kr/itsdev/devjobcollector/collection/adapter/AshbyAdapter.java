package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.*;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AshbyAdapter implements JobSourceAdapter {
    private static final String VERSION = "ashby-public-job-board-v1";
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    @Override public SourceType sourceType() { return SourceType.ASHBY; }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        if (target == null || target.getProvider() != sourceType()
                || !target.getSourceIdentifier().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Ashby board identifier is required");
        }
        String url = UriComponentsBuilder.fromUriString("https://api.ashbyhq.com/posting-api/job-board")
                .pathSegment(target.getSourceIdentifier()).build().toUriString();
        try {
            String payload = restTemplate.getForObject(url, String.class);
            if (payload == null || payload.isBlank()) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
            JsonNode root = mapper.readTree(payload);
            if (root == null || !root.path("jobs").isArray()) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
            List<JobRawDto> jobs = new ArrayList<>();
            boolean malformed = false;
            for (JsonNode job : root.path("jobs")) {
                if (!job.path("isListed").isBoolean()) { malformed = true; continue; }
                if (!job.path("isListed").asBoolean()) continue;
                String title = value(job, "title"), jobUrl = value(job, "jobUrl");
                if (title == null || !validUrl(jobUrl)) { malformed = true; continue; }
                String raw = job.toString();
                String id = ContentHash.sha256(jobUrl);
                jobs.add(new JobRawDto(sourceType(), id, title, value(job, "location"),
                        value(job, "employmentType"), value(job, "department"), jobUrl,
                        validUrl(value(job, "applyUrl")) ? value(job, "applyUrl") : jobUrl,
                        value(job, "descriptionPlain"), instant(value(job, "publishedAt")), null,
                        ContentHash.sha256(raw), raw));
            }
            CollectionStatus status = malformed ? CollectionStatus.PARTIAL_SUCCESS
                    : jobs.isEmpty() ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS;
            return new CollectionResult(status, jobs, jobs.size(), VERSION, !malformed && !jobs.isEmpty(), ContentHash.sha256(payload));
        } catch (HttpStatusCodeException e) {
            return CollectionResult.failed(e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED, VERSION);
        } catch (java.io.IOException e) {
            return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
        } catch (RestClientException e) {
            return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
        }
    }

    private static String value(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }
    private static boolean validUrl(String value) {
        if (value == null) return false;
        try { URI uri = URI.create(value); return "https".equalsIgnoreCase(uri.getScheme()) && "jobs.ashbyhq.com".equalsIgnoreCase(uri.getHost()); }
        catch (IllegalArgumentException e) { return false; }
    }
    private static Instant instant(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException e) { return null; }
    }
}
