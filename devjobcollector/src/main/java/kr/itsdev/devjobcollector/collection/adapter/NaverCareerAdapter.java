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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverCareerAdapter implements JobSourceAdapter {

    static final String LIST_URL = "https://recruit.navercorp.com/rcrt/loadJobList.do";
    private static final String DETAIL_URL = "https://recruit.navercorp.com/rcrt/view.do?annoId=";
    private static final String SCHEMA_VERSION = "naver-careers-v1-2026-08";
    private static final int MAX_PAGES = 100;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter NAVER_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    private static final Map<String, String> WORK_AREAS = Map.of(
            "0010", "분당",
            "0020", "서울",
            "0030", "춘천",
            "0040", "세종",
            "0050", "Global"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CompanyPageCollectionProperties properties;

    @Override
    public SourceType sourceType() {
        return SourceType.NAVER_CAREERS;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);
        try {
            ListResponse pages = fetchAllPages();
            if (!pages.complete()) {
                return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
            }
            if (pages.jobs().isEmpty()) {
                return new CollectionResult(CollectionStatus.EMPTY_SUCCESS, List.of(), 0,
                        SCHEMA_VERSION, false, ContentHash.sha256(pages.rawResponse()));
            }

            boolean detailFailure = false;
            boolean malformedJob = false;
            Map<Long, JobRawDto> uniqueJobs = new LinkedHashMap<>();
            for (NaverCareerResponse.Job sourceJob : pages.jobs()) {
                if (!isValid(sourceJob)) {
                    malformedJob = true;
                    continue;
                }
                DetailData detail;
                try {
                    detail = fetchDetail(sourceJob);
                } catch (RestClientException e) {
                    detailFailure = true;
                    detail = DetailData.empty();
                    log.warn("Naver career detail collection failed: annoId={}", sourceJob.annoId());
                }
                uniqueJobs.putIfAbsent(sourceJob.annoId(), toRawJob(target, sourceJob, detail));
            }

            boolean completeJobSet = !malformedJob
                    && uniqueJobs.size() == pages.totalSize();
            CollectionStatus status = detailFailure || !completeJobSet
                    ? CollectionStatus.PARTIAL_SUCCESS : CollectionStatus.SUCCESS;
            List<JobRawDto> jobs = List.copyOf(uniqueJobs.values());
            return new CollectionResult(status, jobs, jobs.size(), SCHEMA_VERSION,
                    completeJobSet, ContentHash.sha256(pages.rawResponse()));
        } catch (HttpStatusCodeException e) {
            CollectionStatus status = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Naver career collection failed: target={}, status={}",
                    target.getSourceIdentifier(), e.getStatusCode().value());
            return CollectionResult.failed(status, SCHEMA_VERSION);
        } catch (JsonProcessingException e) {
            log.warn("Naver career schema changed: target={}", target.getSourceIdentifier(), e);
            return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
        } catch (RestClientException e) {
            log.warn("Naver career request failed: target={}", target.getSourceIdentifier());
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }
    }

    private ListResponse fetchAllPages() throws JsonProcessingException {
        List<NaverCareerResponse.Job> jobs = new ArrayList<>();
        StringBuilder rawResponse = new StringBuilder();
        Integer expectedTotal = null;
        int firstIndex = 0;

        for (int page = 0; page < MAX_PAGES; page++) {
            String rawPage = fetchText(URI.create(LIST_URL + "?firstIndex=" + firstIndex),
                    MediaType.APPLICATION_JSON);
            if (rawPage == null || rawPage.isBlank()) {
                return new ListResponse(List.of(), 0, rawResponse.toString(), false);
            }
            rawResponse.append(rawPage);
            NaverCareerResponse response = objectMapper.readValue(rawPage, NaverCareerResponse.class);
            if (response.totalSize() == null || response.totalSize() < 0 || response.list() == null) {
                return new ListResponse(List.of(), 0, rawResponse.toString(), false);
            }
            if (expectedTotal == null) {
                expectedTotal = response.totalSize();
            } else if (!expectedTotal.equals(response.totalSize())) {
                return new ListResponse(List.copyOf(jobs), expectedTotal, rawResponse.toString(), false);
            }

            List<NaverCareerResponse.Job> pageJobs = response.list().stream()
                    .filter(Objects::nonNull)
                    .toList();
            jobs.addAll(pageJobs);
            firstIndex += response.list().size();
            if (firstIndex >= expectedTotal) {
                return new ListResponse(List.copyOf(jobs), expectedTotal, rawResponse.toString(),
                        jobs.size() == expectedTotal);
            }
            if (response.list().isEmpty()) {
                return new ListResponse(List.copyOf(jobs), expectedTotal, rawResponse.toString(), false);
            }
        }
        return new ListResponse(List.copyOf(jobs), expectedTotal == null ? 0 : expectedTotal,
                rawResponse.toString(), false);
    }

    private DetailData fetchDetail(NaverCareerResponse.Job job) {
        URI detailUri = detailUri(job);
        String html = fetchText(detailUri, MediaType.TEXT_HTML);
        if (html == null || html.isBlank()) {
            throw new RestClientException("Empty Naver career detail response");
        }
        Document document = Jsoup.parse(html, detailUri.toString());
        String description = document.select(".detail_box").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n\n"));
        return new DetailData(
                truncate(description),
                jsonLdLocation(document)
        );
    }

    private String fetchText(URI uri, MediaType accept) {
        RequestEntity<Void> request = RequestEntity.get(uri)
                .header(HttpHeaders.USER_AGENT, properties.userAgent())
                .accept(accept)
                .build();
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        return response.getBody();
    }

    private JobRawDto toRawJob(CompanySourceTarget target, NaverCareerResponse.Job job,
                               DetailData detail) {
        String sourceUrl = detailUri(job).toString();
        String location = firstNonBlank(detail.location(), WORK_AREAS.get(job.workAreaCd()));
        String team = joinNonBlank(job.classCdNm(), job.subJobCdNm());
        String rawPayload = writePayload(new RawPayload(job, detail));
        return new JobRawDto(
                sourceType(),
                String.valueOf(job.annoId()),
                firstNonBlank(job.sysCompanyCdNm(), target.displayCompanyName()),
                job.annoSubject(),
                location,
                job.empTypeCdNm(),
                job.entTypeCdNm(),
                team,
                sourceUrl,
                sourceUrl,
                detail.description(),
                parseDateTime(job.staYmdTime(), false),
                null,
                parseDateTime(job.endYmdTime(), true),
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    private String jsonLdLocation(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String payload = script.data().isBlank() ? script.html() : script.data();
            if (payload.isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(payload);
                if (!"JobPosting".equalsIgnoreCase(node.path("@type").asText())) {
                    continue;
                }
                String streetAddress = node.path("jobLocation").path("address")
                        .path("streetAddress").asText().trim();
                if (!streetAddress.isBlank()) {
                    return streetAddress;
                }
            } catch (JsonProcessingException ignored) {
                // Other JSON-LD scripts must not prevent collecting the posting.
            }
        }
        return null;
    }

    private static Instant parseDateTime(String value, boolean deadline) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(value.trim(), NAVER_DATE_TIME);
            if (deadline && dateTime.getYear() >= 2999) {
                return null;
            }
            return dateTime.atZone(SERVICE_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private URI detailUri(NaverCareerResponse.Job job) {
        String value = firstNonBlank(job.jobDetailLink(), DETAIL_URL + job.annoId() + "&lang=ko");
        URI uri = URI.create(value);
        return uri.isAbsolute() ? uri : URI.create(LIST_URL).resolve(uri);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= properties.maxDescriptionLength()
                ? value : value.substring(0, properties.maxDescriptionLength());
    }

    private String writePayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Naver career job", e);
        }
    }

    private static boolean isValid(NaverCareerResponse.Job job) {
        return job != null && job.annoId() != null
                && job.annoSubject() != null && !job.annoSubject().isBlank();
    }

    private static String joinNonBlank(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank() || first.equals(second)) {
            return first;
        }
        return first + " / " + second;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Naver career target is required");
        }
    }

    private record ListResponse(
            List<NaverCareerResponse.Job> jobs,
            int totalSize,
            String rawResponse,
            boolean complete
    ) {
    }

    private record DetailData(String description, String location) {
        private static DetailData empty() {
            return new DetailData(null, null);
        }
    }

    private record RawPayload(NaverCareerResponse.Job job, DetailData detail) {
    }
}
