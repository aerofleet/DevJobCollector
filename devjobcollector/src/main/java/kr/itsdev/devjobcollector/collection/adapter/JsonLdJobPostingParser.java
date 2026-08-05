package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.CompanyPageCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonLdJobPostingParser {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;
    private final CompanyPageCollectionProperties properties;

    public ParseResult parse(String html, URI pageUri, String fallbackCompanyName) {
        Document document = Jsoup.parse(html, pageUri.toString());
        List<JsonNode> postings = new ArrayList<>();
        int scriptCount = 0;
        int malformedScriptCount = 0;

        for (Element script : document.select("script[type=application/ld+json]")) {
            String payload = script.data().isBlank() ? script.html() : script.data();
            if (payload.isBlank()) {
                continue;
            }
            scriptCount++;
            try {
                collectJobPostings(objectMapper.readTree(payload), postings);
            } catch (JsonProcessingException e) {
                malformedScriptCount++;
            }
        }

        Map<String, JobRawDto> uniqueJobs = new LinkedHashMap<>();
        for (JsonNode posting : postings) {
            JobRawDto job = toRawJob(posting, pageUri, fallbackCompanyName);
            if (job != null) {
                uniqueJobs.putIfAbsent(job.sourceJobId(), job);
            }
        }
        return new ParseResult(List.copyOf(uniqueJobs.values()), scriptCount, malformedScriptCount);
    }

    private void collectJobPostings(JsonNode node, List<JsonNode> output) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectJobPostings(child, output));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (hasType(node.get("@type"), "JobPosting")) {
            output.add(node);
            return;
        }
        node.elements().forEachRemaining(child -> collectJobPostings(child, output));
    }

    private JobRawDto toRawJob(JsonNode posting, URI pageUri, String fallbackCompanyName) {
        String title = text(posting.get("title"));
        String sourceUrl = resolveUrl(pageUri, firstText(posting, "url", "@id"));
        if (sourceUrl == null) {
            sourceUrl = pageUri.toString();
        }
        if (title == null || title.isBlank()) {
            return null;
        }

        String identifier = identifier(posting.get("identifier"));
        if (identifier == null) {
            identifier = sourceUrl;
        }
        String sourceJobId = identifier.length() <= 180
                ? identifier : ContentHash.sha256(identifier);
        String rawPayload = posting.toString();
        String description = plainDescription(text(posting.get("description")));

        return new JobRawDto(
                SourceType.COMPANY_PAGE,
                sourceJobId,
                companyName(posting.get("hiringOrganization"), fallbackCompanyName),
                title,
                location(posting.get("jobLocation")),
                joinValues(posting.get("employmentType")),
                experience(posting.get("experienceRequirements")),
                firstText(posting, "occupationalCategory", "industry"),
                sourceUrl,
                sourceUrl,
                description,
                parseInstant(text(posting.get("datePosted")), false),
                parseInstant(firstText(posting, "dateModified", "datePosted"), false),
                parseInstant(text(posting.get("validThrough")), true),
                ContentHash.sha256(rawPayload),
                rawPayload
        );
    }

    private String plainDescription(String html) {
        if (html == null) {
            return null;
        }
        String plainText = Jsoup.parse(html).text().replaceAll("\\s+", " ").trim();
        return plainText.length() <= properties.maxDescriptionLength()
                ? plainText : plainText.substring(0, properties.maxDescriptionLength());
    }

    private static String companyName(JsonNode organization, String fallback) {
        String value = organization == null ? null : firstText(organization, "name", "legalName");
        return value == null ? fallback : value;
    }

    private static String experience(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isArray()) {
            return joinValues(value);
        }
        return firstText(value, "name", "value", "monthsOfExperience");
    }

    private static String location(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isArray()) {
            List<String> locations = new ArrayList<>();
            value.forEach(item -> addIfPresent(locations, location(item)));
            return locations.isEmpty() ? null : String.join(", ", locations);
        }
        JsonNode address = value.has("address") ? value.get("address") : value;
        if (address.isTextual()) {
            return text(address);
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, text(address.get("addressRegion")));
        addIfPresent(parts, text(address.get("addressLocality")));
        addIfPresent(parts, text(address.get("streetAddress")));
        String country = address.has("addressCountry")
                ? firstTextOrScalar(address.get("addressCountry"), "name") : null;
        addIfPresent(parts, country);
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static String identifier(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isValueNode()) {
            return text(value);
        }
        return firstText(value, "value", "name", "@id");
    }

    private static String joinValues(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            return firstTextOrScalar(value, "name");
        }
        List<String> values = new ArrayList<>();
        value.forEach(item -> addIfPresent(values, firstTextOrScalar(item, "name")));
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private static String firstText(JsonNode object, String... fields) {
        if (object == null || !object.isObject()) {
            return null;
        }
        for (String field : fields) {
            String value = text(object.get(field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstTextOrScalar(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isValueNode() ? text(value) : firstText(value, field, "value");
    }

    private static String text(JsonNode value) {
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean hasType(JsonNode type, String expected) {
        if (type == null || type.isNull()) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode value : type) {
                if (expected.equalsIgnoreCase(text(value))) {
                    return true;
                }
            }
            return false;
        }
        return expected.equalsIgnoreCase(text(type));
    }

    private static String resolveUrl(URI baseUri, String value) {
        if (value == null) {
            return null;
        }
        try {
            URI resolved = baseUri.resolve(value);
            String scheme = resolved.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    ? resolved.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant parseInstant(String value, boolean endOfDay) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    LocalDate date = LocalDate.parse(value);
                    return endOfDay
                            ? date.plusDays(1).atStartOfDay(SERVICE_ZONE).minusNanos(1).toInstant()
                            : date.atStartOfDay(SERVICE_ZONE).toInstant();
                } catch (DateTimeParseException ignoredDate) {
                    return null;
                }
            }
        }
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    public record ParseResult(
            List<JobRawDto> jobs,
            int jsonLdScriptCount,
            int malformedScriptCount
    ) {
    }
}
