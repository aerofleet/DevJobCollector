package kr.itsdev.devjobcollector.collection.adapter;

import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.*;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import java.time.Instant;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/** Collects the public openings displayed on a configured Greeting career page. */
@Component
public class GreetingAdapter implements JobSourceAdapter {
    public static final String VERSION = "greeting-openings-v2";
    private static final String USER_AGENT = "DevJobCollector/1.0 (+https://itsdev.kr)";
    private static final int MAX_BYTES = 2_000_000;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override public SourceType sourceType() { return SourceType.GREETING; }

    @Override
    public CollectionResult fetchJobs(CompanySourceTarget target, CollectionContext context) {
        if (target == null || target.getProvider() != sourceType()
                || !target.getSourceIdentifier().matches("[a-z0-9-]{1,63}")) {
            throw new IllegalArgumentException("Greeting board identifier is required");
        }
        String origin = "https://" + target.getSourceIdentifier() + ".career.greetinghr.com";
        try {
            URI careers = URI.create(target.getCareersUrl());
            if (!"https".equalsIgnoreCase(careers.getScheme()) || careers.getUserInfo() != null
                    || !origin.equals("https://" + careers.getHost()) || careers.getPort() != -1
                    || careers.getRawQuery() != null || careers.getRawFragment() != null
                    || careers.getPath() == null || !careers.getPath().matches("/[a-zA-Z0-9/_-]+")) {
                return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
            }
            String robots = get(URI.create(origin + "/robots.txt"));
            if (robots == null || !allowed(robots, careers.getRawPath())) return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
            String html = get(careers);
            if (html == null) return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
            CollectionResult listing = parseList(html, origin, robots);
            if (listing.status() != CollectionStatus.SUCCESS || listing.jobs().isEmpty()) return listing;
            List<JobRawDto> enriched = new ArrayList<>();
            boolean incomplete = false;
            for (JobRawDto job : listing.jobs()) {
                try {
                    Thread.sleep(250);
                    String detail = get(URI.create(job.sourceUrl()));
                    JobRawDto enrichedJob = detail == null ? null : parseDetail(job, detail);
                    if (enrichedJob == null) incomplete = true;
                    else enriched.add(enrichedJob);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
                } catch (Exception e) {
                    incomplete = true;
                }
            }
            if (enriched.isEmpty() && incomplete) return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
            return new CollectionResult(incomplete ? CollectionStatus.PARTIAL_SUCCESS : CollectionStatus.SUCCESS,
                    enriched, enriched.size(), VERSION, !incomplete, listing.responseHash());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
        } catch (Exception e) {
            return CollectionResult.failed(CollectionStatus.FAILED, VERSION);
        }
    }

    static JobRawDto parseDetail(JobRawDto listing, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        String json = doc.select("script#__NEXT_DATA__[type=application/json]").html();
        if (json.isBlank()) return null;
        JsonNode queries = MAPPER.readTree(json).path("props").path("pageProps").path("dehydratedState").path("queries");
        JsonNode detail = null;
        for (JsonNode query : queries) {
            JsonNode key = query.path("queryKey");
            if (key.isArray() && key.size() >= 2 && "career".equals(key.get(0).asText())
                    && "getOpeningById".equals(key.get(1).asText())) {
                if (detail != null || !query.path("state").path("data").path("success").asBoolean(false)) return null;
                detail = query.path("state").path("data").path("data");
            }
        }
        if (detail == null || !detail.isObject()) return null;
        JsonNode opening = detail.path("openingsInfo");
        if (!listing.sourceJobId().equals(opening.path("openingId").asText())
                || !"OPEN".equals(opening.path("status").asText())
                || !opening.path("deploy").asBoolean(false) || !opening.path("detail").isTextual()) return null;
        String description = extractRelevantText(opening.path("detail").asText());
        if (description.isBlank()) return null;
        List<String> places = new ArrayList<>();
        String experience = null, employment = null, team = null;
        for (JsonNode position : detail.path("jobPositionSetting").path("jobPositions")) {
            JsonNode place = position.path("workspacePlace");
            String address = place.path("place").asText("").trim();
            String extra = place.path("detailPlace").asText("").trim();
            if (!address.isEmpty()) { String full = extra.isEmpty() ? address : address + " " + extra; if (!places.contains(full)) places.add(full); }
            JsonNode career = position.path("jobPositionCareer");
            if ("EXPERIENCED".equals(career.path("careerType").asText()) && career.path("careerFrom").asInt(0) > 0) {
                int from = career.path("careerFrom").asInt();
                int to = career.path("careerTo").asInt(0);
                experience = to >= from ? "경력 " + from + "~" + to + "년" : "경력 " + from + "년 이상";
            }
            if ("FULL_TIME_WORKER".equals(position.path("jobPositionEmployment").path("employmentType").asText())) employment = "정규직";
            String job = position.path("workspaceJob").path("job").asText("").trim();
            if (!job.isEmpty()) team = job;
        }
        var raw = MAPPER.createObjectNode().put("id", listing.sourceJobId()).put("url", listing.sourceUrl())
                .put("title", listing.title()).put("description", description);
        if (!places.isEmpty()) raw.put("location", String.join(", ", places));
        if (experience != null) raw.put("experience", experience);
        if (employment != null) raw.put("employmentType", employment);
        if (team != null) raw.put("jobCategory", team);
        String payload = raw.toString();
        return new JobRawDto(SourceType.GREETING, listing.sourceJobId(), null, listing.title(),
                places.isEmpty() ? null : String.join(", ", places), employment, experience, team,
                listing.sourceUrl(), listing.applyUrl(), description, listing.publishedAt(), null,
                listing.deadlineAt(), ContentHash.sha256(payload), payload);
    }

    static String extractRelevantText(String html) {
        String text = Jsoup.parse(html.replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])>", "\n"))
                .body().wholeText().replace('\u00a0', ' ');
        StringBuilder result = new StringBuilder();
        boolean collecting = false;
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (value.matches("(?i)^(?:주요\\s*업무|담당\\s*업무|업무\\s*내용|자격\\s*요건|지원\\s*자격|필수\\s*(?:요건|사항|조건)|우대\\s*(?:요건|사항|조건)|급여\\s*(?:정보|조건)?|연봉|채용\\s*프로세스|채용\\s*절차|혜택\\s*및\\s*복지)\\s*[:：]?$")) collecting = true;
            if (collecting) result.append(value).append('\n');
            if (result.length() >= 20000) break;
        }
        return result.length() > 20000 ? result.substring(0, 20000) : result.toString().trim();
    }

    static CollectionResult parseList(String html, String origin, String robots) throws Exception {
        Document doc = Jsoup.parse(html);
        String json = doc.select("script#__NEXT_DATA__[type=application/json]").html();
        JsonNode queries = MAPPER.readTree(json).path("props").path("pageProps").path("dehydratedState").path("queries");
        if (!queries.isArray()) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
        JsonNode openings = null;
        for (JsonNode query : queries) {
            JsonNode key = query.path("queryKey");
            if ((key.isTextual() && key.asText().equals("openings"))
                    || (key.isArray() && key.size() == 1 && key.get(0).isTextual()
                    && key.get(0).asText().equals("openings"))) {
                if (openings != null) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
                openings = query.path("state").path("data");
            }
        }
        if (openings == null || !openings.isArray()) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
        Set<String> rendered = new LinkedHashSet<>();
        for (var link : doc.select("a[data-testid=공고_아이템][href]")) {
            String href = link.attr("href");
            if (!href.matches("/(?:ko/)?o/[0-9]+")) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
            if (!rendered.add(href.substring(href.lastIndexOf('/') + 1))) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
        }
        Map<String, JobRawDto> jobs = new LinkedHashMap<>();
        for (JsonNode job : openings) {
            if (!job.path("deploy").isBoolean() || !job.path("deploy").asBoolean()
                    || !job.path("openingId").isIntegralNumber() || !job.path("title").isTextual()
                    || job.path("title").asText().isBlank()) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
            String id = job.path("openingId").asText(), path = "/o/" + id, url = origin + path;
            if (!allowed(robots, path) || jobs.containsKey(id)) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
            String title = job.path("title").asText().trim();
            String raw = MAPPER.createObjectNode().put("title", title).put("url", url).toString();
            jobs.put(id, new JobRawDto(SourceType.GREETING, id, null, title, null, null, null, null,
                    url, url, null, parseTime(job.path("openDate")), null, parseTime(job.path("dueDate")), ContentHash.sha256(raw), raw));
        }
        if (!rendered.equals(jobs.keySet())) return CollectionResult.failed(CollectionStatus.SCHEMA_CHANGED, VERSION);
        List<JobRawDto> list = List.copyOf(jobs.values());
        return new CollectionResult(list.isEmpty() ? CollectionStatus.EMPTY_SUCCESS : CollectionStatus.SUCCESS,
                list, list.size(), VERSION, !list.isEmpty(), ContentHash.sha256(html));
    }

    private static Instant parseTime(JsonNode value) {
        if (!value.isTextual()) return null;
        try { return Instant.parse(value.asText()); } catch (java.time.format.DateTimeParseException e) { return null; }
    }

    private String get(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) return null;
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            return data.length > MAX_BYTES ? null : new String(data, StandardCharsets.UTF_8);
        }
    }

    static List<String> postingUrls(String xml, String origin) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Jsoup.parse(xml, "", Parser.xmlParser()).select("url > loc").forEach(element -> {
            String url = element.text().trim();
            if (url.matches(Pattern.quote(origin) + "/o/[0-9]+")) result.add(url);
        });
        return List.copyOf(result);
    }

    static boolean allowed(String robots, String path) {
        boolean relevant = false, haveRules = false, applies = false;
        int bestAllow = -1, bestDeny = -1;
        for (String line : robots.split("\\R")) {
            String clean = line.split("#", 2)[0].trim();
            int colon = clean.indexOf(':');
            if (colon < 0) continue;
            String key = clean.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = clean.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (haveRules) { relevant = false; haveRules = false; }
                relevant |= value.equals("*") || "devjobcollector".equalsIgnoreCase(value);
            } else if (key.equals("allow") || key.equals("disallow")) {
                haveRules = true;
                if (!relevant || value.isEmpty()) continue;
                applies = true;
                String regex = "^" + Pattern.quote(value).replace("*", "\\E.*\\Q").replace("$", "\\E$\\Q");
                if (Pattern.compile(regex).matcher(path).find()) {
                    int specificity = value.replace("*", "").replace("$", "").length();
                    if (key.equals("allow")) bestAllow = Math.max(bestAllow, specificity);
                    else bestDeny = Math.max(bestDeny, specificity);
                }
            } else if (key.equals("content-signal") && relevant && value.toLowerCase(Locale.ROOT).matches(".*search\\s*=\\s*no.*")) {
                return false;
            }
        }
        return applies && bestAllow >= bestDeny;
    }
}
