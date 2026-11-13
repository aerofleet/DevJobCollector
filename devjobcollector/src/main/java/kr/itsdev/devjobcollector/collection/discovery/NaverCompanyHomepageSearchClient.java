package kr.itsdev.devjobcollector.collection.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Optional Naver Web Search integration used to suggest, never certify, company homepages. */
@Component
public class NaverCompanyHomepageSearchClient {
    private static final String ENDPOINT = "https://naverapihub.apigw.ntruss.com/search/v1/webkr";
    private static final List<String> BLOCKED_HOSTS = List.of("naver.com", "daum.net", "google.com", "youtube.com",
            "facebook.com", "instagram.com", "linkedin.com", "saramin.co.kr", "jobkorea.co.kr", "wanted.co.kr",
            "catch.co.kr", "incruit.com", "work.go.kr", "bizno.net");
    private final RestTemplate restTemplate;
    private final NaverSearchApiQuotaService quota;
    private final String clientId;
    private final String clientSecret;

    public NaverCompanyHomepageSearchClient(RestTemplate restTemplate, NaverSearchApiQuotaService quota,
            @Value("${collection.homepage-search.naver-client-id:}") String clientId,
            @Value("${collection.homepage-search.naver-client-secret:}") String clientSecret) {
        this.restTemplate = restTemplate;
        this.quota = quota;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean isConfigured() { return !clientId.isBlank() && !clientSecret.isBlank(); }

    public List<Suggestion> search(String companyName) {
        if (!isConfigured()) return List.of();
        if (!quota.reserveCall()) throw new SearchQuotaExceededException();
        String query = "\"" + searchName(companyName) + "\" 공식 홈페이지";
        URI uri = UriComponentsBuilder.fromUriString(ENDPOINT).queryParam("query", query)
                .queryParam("display", 10).queryParam("start", 1).build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-NCP-APIGW-API-KEY-ID", clientId);
        headers.set("X-NCP-APIGW-API-KEY", clientSecret);
        JsonNode root = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
        if (root == null || !root.path("items").isArray()) return List.of();
        List<Suggestion> suggestions = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            String raw = item.path("link").asText("");
            try {
                URI link = URI.create(raw);
                String host = link.getHost();
                if (!"https".equalsIgnoreCase(link.getScheme()) || host == null || isBlocked(host)) continue;
                String title = Jsoup.parse(item.path("title").asText("")).text();
                String description = Jsoup.parse(item.path("description").asText("")).text();
                int score = score(companyName, title, description, link);
                if (score >= 45) suggestions.add(new Suggestion(link.toString(), title, score));
            } catch (IllegalArgumentException ignored) { }
        }
        return suggestions.stream().sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .distinct().limit(3).toList();
    }

    private static int score(String companyName, String title, String description, URI link) {
        String company = searchName(companyName);
        String resultTitle = normalize(title);
        String resultDescription = normalize(description);
        int score = 0;
        if (!company.isBlank() && resultTitle.contains(company)) score += 55;
        if (!company.isBlank() && resultDescription.contains(company)) score += 15;
        if (link.getPath() == null || link.getPath().isBlank() || "/".equals(link.getPath())) score += 10;
        return Math.min(score, 100);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
    }
    private static String searchName(String value) {
        return normalize(value).replaceFirst("^(주식회사|유한회사|합자회사|합명회사|주|유)", "")
                .replaceFirst("(주식회사|유한회사|합자회사|합명회사|주|유)$", "");
    }
    private static boolean isBlocked(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return BLOCKED_HOSTS.stream().anyMatch(blocked -> normalized.equals(blocked) || normalized.endsWith("." + blocked));
    }

    public record Suggestion(String url, String title, int score) { }

    public static class SearchQuotaExceededException extends RuntimeException { }
}
