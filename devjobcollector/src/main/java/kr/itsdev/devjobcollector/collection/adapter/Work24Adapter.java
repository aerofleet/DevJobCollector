package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.Work24CollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
public class Work24Adapter implements JobSourceAdapter {
    private static final String ENDPOINT = "https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo210L01.do";
    private static final String VERSION = "work24-list-v1";
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final Work24CollectionProperties properties;
    private final Work24ResponseParser parser = new Work24ResponseParser();

    public Work24Adapter(RestTemplate restTemplate, ObjectMapper mapper, Work24CollectionProperties properties) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override public SourceType sourceType() { return SourceType.WORK24; }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        if (target == null || target.getProvider() != sourceType()) throw new IllegalArgumentException("Work24 target required");
        // The operator must supply verified occupation codes; never run an unfiltered query.
        String occupation = target.getSourceIdentifier();
        if (!properties.enabled() || properties.accessKey().isBlank() || occupation.length() > 150
                || !occupation.matches("[0-9]{2,10}(\\|[0-9]{2,10}){0,49}")) {
            return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
        }
        List<JobRawDto> jobs = new ArrayList<>();
        var seen = new HashSet<String>();
        Integer total = null;
        try {
            for (int page = 1; page <= properties.maxPages(); page++) {
                var uri = UriComponentsBuilder.fromUriString(ENDPOINT)
                        .queryParam("authKey", properties.accessKey()).queryParam("callTp", "L")
                        .queryParam("returnType", "XML").queryParam("startPage", page)
                        .queryParam("display", properties.pageSize()).queryParam("occupation", occupation)
                        .build().encode().toUri();
                String body = restTemplate.exchange(RequestEntity.get(uri).accept(MediaType.APPLICATION_XML).build(), String.class).getBody();
                var response = parser.parse(body);
                if (response.page() != page || (total != null && total != response.total())) return partial(jobs);
                total = response.total();
                for (var item : response.jobs()) {
                    if (!seen.add(item.id())) return partial(jobs);
                    // Only the documented public fields needed for review are retained, not the entire API payload.
                    String payload = mapper.writeValueAsString(item);
                    jobs.add(new JobRawDto(sourceType(), item.id(), item.company(), item.title(), item.region(),
                            null, item.career(), item.occupationCode(), item.url(), item.url(), null,
                            null, null, null, ContentHash.sha256(payload), payload));
                }
                if (jobs.size() == total) return result(jobs.isEmpty() ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS, jobs);
                if (jobs.size() > total || response.jobs().isEmpty() || response.jobs().size() < properties.pageSize()) return partial(jobs);
            }
            return partial(jobs);
        } catch (HttpStatusCodeException e) {
            return jobs.isEmpty() ? CollectionResult.failed(e.getStatusCode().value() == 429 ? CollectionStatus.RATE_LIMITED : CollectionStatus.FAILED, VERSION) : partial(jobs);
        } catch (RestClientException | IllegalArgumentException | JsonProcessingException e) {
            return jobs.isEmpty() ? CollectionResult.failed(CollectionStatus.FAILED, VERSION) : partial(jobs);
        }
    }

    private CollectionResult partial(List<JobRawDto> jobs) { return result(CollectionStatus.PARTIAL_SUCCESS, jobs); }
    private CollectionResult result(CollectionStatus status, List<JobRawDto> jobs) {
        String hashes = jobs.stream().map(JobRawDto::contentHash).reduce("", String::concat);
        return new CollectionResult(status, jobs, jobs.size(), VERSION, false, ContentHash.sha256(hashes));
    }
}
