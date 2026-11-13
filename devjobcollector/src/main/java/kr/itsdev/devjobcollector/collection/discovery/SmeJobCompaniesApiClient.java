package kr.itsdev.devjobcollector.collection.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Client for the approved KOSME job-company open API snapshot. */
@Component
public class SmeJobCompaniesApiClient {
    private final RestTemplate restTemplate;
    private final SmeJobCompanySeedReader seedReader;
    private final String baseUrl;
    private final String serviceKey;

    public SmeJobCompaniesApiClient(RestTemplate restTemplate, SmeJobCompanySeedReader seedReader,
            @Value("${data-api.sme-job-companies.base-url}") String baseUrl,
            @Value("${data-api.sme-job-companies.service-key:}") String serviceKey) {
        this.restTemplate = restTemplate;
        this.seedReader = seedReader;
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public boolean isConfigured() { return serviceKey != null && !serviceKey.isBlank(); }

    public Page fetchPage(int page, int perPage) {
        if (!isConfigured()) throw new IllegalStateException("KOSME API key is not configured");
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).queryParam("page", page)
                .queryParam("perPage", perPage).queryParam("returnType", "JSON").build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Infuser " + serviceKey);
        JsonNode root = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
        if (root == null || !root.path("data").isArray()) throw new IllegalStateException("KOSME API returned an invalid page");
        List<SmeJobCompanySeedReader.Seed> seeds = new ArrayList<>();
        int skipped = 0;
        for (JsonNode row : root.path("data")) {
            String registration = row.path("사업자번호").asText("").trim();
            if (registration.matches("[0-9]{1,10}")) registration = "0".repeat(10 - registration.length()) + registration;
            var seed = seedReader.createSeed(row.path("기업명").asText(null), registration,
                    row.path("공고명").asText(null), row.path("공고등록일").asText(null));
            if (seed == null) skipped++;
            else seeds.add(seed);
        }
        return new Page(root.path("totalCount").asLong(-1), root.path("currentCount").asInt(seeds.size()), seeds, skipped);
    }

    public record Page(long totalCount, int currentCount, List<SmeJobCompanySeedReader.Seed> seeds, int skipped) { }
}
