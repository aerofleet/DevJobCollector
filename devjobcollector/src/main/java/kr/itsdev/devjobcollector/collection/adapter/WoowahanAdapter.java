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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WoowahanAdapter implements JobSourceAdapter {

    private static final String LIST_URL = "https://career.woowahan.com/w1/recruits";
    private static final String DETAIL_URL = "https://career.woowahan.com/w1/recruits/{recruitNumber}";
    private static final String SCHEMA_VERSION = "woowahan-recruits-v1-2026-10";
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> JOB_GROUP_NAMES = Map.ofEntries(
            Map.entry("BA005001", "경영기획"),
            Map.entry("BA005002", "마케팅"),
            Map.entry("BA005003", "영업"),
            Map.entry("BA005004", "고객서비스"),
            Map.entry("BA005005", "미디어"),
            Map.entry("BA005006", "디자인"),
            Map.entry("BA005007", "서비스기획"),
            Map.entry("BA005008", "개발"),
            Map.entry("BA005009", "데이터"),
            Map.entry("BA005010", "인사"),
            Map.entry("BA005011", "재무"),
            Map.entry("BA005012", "법무"),
            Map.entry("BA005013", "물류"),
            Map.entry("BA005014", "기타")
    );

    private static final Map<String, String> EMPLOYMENT_TYPE_NAMES = Map.of(
            "BA002001", "정규직",
            "BA002002", "계약직",
            "BA002003", "인턴"
    );

    private static final Map<String, String> CAREER_TYPE_NAMES = Map.of(
            "BA003001", "신입",
            "BA003002", "경력",
            "BA003003", "경력무관"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SourceType sourceType() {
        return SourceType.WOOWAHAN;
    }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        requireTarget(target);

        try {
            List<WoowahanRecruitResponse.Recruit> allRecruits = fetchAllPages();
            if (allRecruits.isEmpty()) {
                return new CollectionResult(CollectionStatus.EMPTY_SUCCESS, List.of(), 0,
                        SCHEMA_VERSION, false, null);
            }

            List<JobRawDto> jobs = new ArrayList<>();
            int detailFailures = 0;
            for (WoowahanRecruitResponse.Recruit recruit : allRecruits) {
                WoowahanRecruitResponse.Recruit detailed = fetchDetail(recruit.recruitNumber());
                WoowahanRecruitResponse.Recruit source = detailed != null ? detailed : recruit;
                jobs.add(toRawJob(source));
                if (detailed == null) {
                    detailFailures++;
                }
            }

            CollectionStatus status = detailFailures > 0
                    ? CollectionStatus.PARTIAL_SUCCESS : CollectionStatus.SUCCESS;
            String responseHash = ContentHash.sha256(objectMapper.writeValueAsString(allRecruits));
            return new CollectionResult(status, jobs, jobs.size(), SCHEMA_VERSION,
                    detailFailures == 0, responseHash);
        } catch (HttpStatusCodeException e) {
            CollectionStatus status = e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED;
            log.warn("Woowahan collection failed: status={}", e.getStatusCode().value());
            return CollectionResult.failed(status, SCHEMA_VERSION);
        } catch (JsonProcessingException e) {
            log.warn("Woowahan schema changed", e);
            return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, SCHEMA_VERSION);
        } catch (RestClientException e) {
            log.warn("Woowahan request failed", e);
            return CollectionResult.failed(CollectionStatus.FAILED, SCHEMA_VERSION);
        }
    }

    private List<WoowahanRecruitResponse.Recruit> fetchAllPages() throws JsonProcessingException {
        List<WoowahanRecruitResponse.Recruit> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            String url = UriComponentsBuilder.fromUriString(LIST_URL)
                    .queryParam("page", page)
                    .queryParam("size", PAGE_SIZE)
                    .build().toUriString();
            String raw = restTemplate.getForObject(url, String.class);
            WoowahanRecruitResponse response = objectMapper.readValue(raw, WoowahanRecruitResponse.class);
            if (!response.isSuccess() || response.data().list() == null || response.data().list().isEmpty()) {
                break;
            }
            all.addAll(response.data().list());
            if (response.data().pageNumber() >= response.data().totalPageNumber()) {
                break;
            }
        }
        return all;
    }

    private WoowahanRecruitResponse.Recruit fetchDetail(String recruitNumber) {
        try {
            String url = UriComponentsBuilder.fromUriString(DETAIL_URL)
                    .buildAndExpand(recruitNumber).toUriString();
            String raw = restTemplate.getForObject(url, String.class);
            WoowahanDetailResponse detail = objectMapper.readValue(raw, WoowahanDetailResponse.class);
            return detail.isSuccess() ? detail.data() : null;
        } catch (Exception e) {
            log.debug("Woowahan detail fetch failed: recruitNumber={}", recruitNumber, e);
            return null;
        }
    }

    private JobRawDto toRawJob(WoowahanRecruitResponse.Recruit recruit) {
        String rawPayload = writePayload(recruit);
        String jobGroup = resolveCode(JOB_GROUP_NAMES, recruit.jobGroup());
        String employmentType = resolveCode(EMPLOYMENT_TYPE_NAMES, recruit.employmentType());
        String experience = buildExperience(recruit);
        String description = toPlainText(recruit.recruitContents());
        String sourceUrl = "https://career.woowahan.com/w1/recruits/" + recruit.recruitNumber();

        return new JobRawDto(
                sourceType(),
                recruit.recruitNumber(),
                "우아한형제들",
                recruit.recruitName(),
                null,
                employmentType,
                experience,
                jobGroup,
                sourceUrl,
                sourceUrl,
                description,
                parseInstant(recruit.recruitOpenDate()),
                null,
                parseDeadlineInstant(recruit.recruitEndDate()),
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    private static String buildExperience(WoowahanRecruitResponse.Recruit recruit) {
        String careerType = resolveCode(CAREER_TYPE_NAMES, recruit.careerType());
        int min = recruit.careerRestrictionMinYears();
        int max = recruit.careerRestrictionMaxYears();
        if (careerType != null && !"경력".equals(careerType)) {
            return careerType;
        }
        if (min > 0 && max > 0 && max < 99) {
            return "경력 " + min + "~" + max + "년";
        }
        if (min > 0) {
            return "경력 " + min + "년 이상";
        }
        return "경력무관";
    }

    private static String resolveCode(Map<String, String> codeMap, WoowahanRecruitResponse.CodeItem item) {
        if (item == null || item.recruitItemCode() == null) {
            return null;
        }
        return codeMap.getOrDefault(item.recruitItemCode(), null);
    }

    private static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
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
            return LocalDateTime.parse(value, DATE_FORMAT).atZone(KST).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Instant parseDeadlineInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("9999") || value.startsWith("2999")) {
            return null;
        }
        return parseInstant(value);
    }

    private String writePayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Woowahan recruit", e);
        }
    }

    private void requireTarget(CompanySourceTarget target) {
        if (target == null || target.getProvider() != sourceType()) {
            throw new IllegalArgumentException("Woowahan target is required");
        }
    }
}
