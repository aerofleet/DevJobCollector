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
import org.jsoup.Jsoup;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoCareerAdapter implements JobSourceAdapter {

    private static final String BASE_URL = "https://careers.kakao.com/public/api/job-list";
    public static final String SCHEMA_VERSION = "kakao-careers-public-v2-2026-11";
    private static final int MAX_PAGES = 20;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> JOB_PARTS = List.of(
            "TECHNOLOGY", "DESIGN", "BUSINESS_SERVICES", "STAFF");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SourceType sourceType() {
        return SourceType.KAKAO;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);

        try {
            List<KakaoCareerResponse.Job> allJobs = new ArrayList<>();
            StringBuilder hashSource = new StringBuilder();
            int partFailures = 0;

            for (String part : JOB_PARTS) {
                try {
                    List<KakaoCareerResponse.Job> partJobs = fetchAllPages(part);
                    allJobs.addAll(partJobs);
                    hashSource.append(part).append(':').append(partJobs.size()).append(';');
                } catch (RestClientException e) {
                    log.warn("Kakao collection failed for part={}", part, e);
                    partFailures++;
                }
            }

            if (allJobs.isEmpty() && partFailures == JOB_PARTS.size()) {
                return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
            }

            List<JobRawDto> jobs = allJobs.stream().map(this::toRawJob).toList();
            CollectionStatus status;
            if (partFailures > 0) {
                status = CollectionStatus.PARTIAL_SUCCESS;
            } else {
                status = jobs.isEmpty() ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS;
            }
            String responseHash = ContentHash.sha256(
                    hashSource + objectMapper.writeValueAsString(allJobs));
            return new CollectionResult(status, jobs, jobs.size(), SCHEMA_VERSION,
                    partFailures == 0 && !jobs.isEmpty(), responseHash);
        } catch (HttpStatusCodeException e) {
            CollectionStatus status = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Kakao collection failed: status={}", e.getStatusCode().value());
            return CollectionResult.failed(status, SCHEMA_VERSION);
        } catch (JsonProcessingException e) {
            log.warn("Kakao schema changed", e);
            return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
        } catch (RestClientException e) {
            log.warn("Kakao request failed", e);
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }
    }

    private List<KakaoCareerResponse.Job> fetchAllPages(String part) throws JsonProcessingException {
        List<KakaoCareerResponse.Job> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String url = UriComponentsBuilder.fromUriString(BASE_URL)
                    .queryParam("page", page)
                    .queryParam("company", "ALL")
                    .queryParam("part", part)
                    .queryParam("keyword", "")
                    .queryParam("skilset", "")
                    .queryParam("employeeType", "")
                    .build().toUriString();
            String raw = restTemplate.getForObject(url, String.class);
            KakaoCareerResponse response = objectMapper.readValue(raw, KakaoCareerResponse.class);
            if (response.jobList() == null || response.jobList().isEmpty()) {
                break;
            }
            all.addAll(response.jobList().stream()
                    .filter(j -> !j.closeFlag() && !j.privateFlag())
                    .toList());
            if (page >= response.totalPage()) {
                break;
            }
        }
        return all;
    }

    private JobRawDto toRawJob(KakaoCareerResponse.Job job) {
        String rawPayload = writePayload(job);
        String companyName = job.companyName() != null && !job.companyName().isBlank()
                ? job.companyName() : "카카오";
        String team = job.jobPartName() != null && !job.jobPartName().isBlank()
                ? job.jobPartName() : job.jobTypeName();
        String sourceUrl = "https://careers.kakao.com/jobs/" + job.realId();

        return new JobRawDto(
                sourceType(),
                job.realId(),
                companyName,
                job.jobOfferTitle(),
                job.locationName(),
                job.employeeTypeName(),
                experienceFromTitle(job.jobOfferTitle()),
                team,
                sourceUrl,
                sourceUrl,
                description(job),
                parseInstant(job.regDate()),
                parseInstant(job.uptDate()),
                parseDeadlineInstant(job.endDate()),
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    static String description(KakaoCareerResponse.Job job) {
        List<String> parts = new ArrayList<>();
        appendSection(parts, "조직 소개", job.introduction());
        appendSection(parts, "업무내용", job.workContentDesc());
        appendSection(parts, "자격요건", job.qualification());
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    static String experienceFromTitle(String title) {
        return title != null && title.matches(".*[\\(\\[]\\s*신입\\s*[/·,]\\s*경력\\s*[\\)\\]].*") ? "신입·경력" : null;
    }

    private static void appendSection(List<String> parts, String heading, String html) {
        String content = toPlainText(html);
        if (content != null && !content.isBlank()) parts.add(heading + "\n" + content);
    }

    private static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String decoded = HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(html))
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])\\s*>", "\n")
                .replaceAll("(?i)<li(?:\\s[^>]*)?>", "\n• ");
        return Jsoup.parse(decoded).body().wholeText().replace('\u00a0', ' ')
                .replaceAll("(?m)^[ \\t]*\\*\\*[ \\t]*◆[ \\t]*(우대사항|우대요건|우대조건)[ \\t]*\\*\\*[ \\t]*$", "$1")
                .replaceAll("(?m)[ \\t]+$", "").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value).atZone(KST).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Instant parseDeadlineInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseInstant(value);
    }

    private String writePayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Kakao job", e);
        }
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Kakao target is required");
        }
    }
}
