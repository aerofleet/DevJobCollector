package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.List;

/** Extracts source text only after the page identifies the requested posting. */
public final class StripeSalaryEvidenceParser {
    public record Result(String status, List<String> paragraphs) { }

    private final ObjectMapper mapper;
    public StripeSalaryEvidenceParser(ObjectMapper mapper) { this.mapper = mapper; }

    public Result parse(String html, String expectedJobId) {
        if (html == null || html.isBlank() || expectedJobId == null || !expectedJobId.matches("[0-9]+"))
            return new Result("UNAVAILABLE", List.of());
        var document = Jsoup.parse(html);
        int matching = 0;
        int postings = 0;
        boolean malformed = false;
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                var counts = countPostings(mapper.readTree(script.data()), expectedJobId);
                matching += counts[0];
                postings += counts[1];
            } catch (JsonProcessingException e) { malformed = true; }
        }
        if (malformed || matching != 1 || postings != 1) return new Result("UNAVAILABLE", List.of());
        var sections = document.select(".careers-listing-details__body").stream()
                .filter(section -> section.select("h2").stream().anyMatch(h -> h.text().trim().equalsIgnoreCase("Pay and benefits")))
                .toList();
        // A missing section can mean a changed or incomplete page, not absent compensation.
        if (sections.size() != 1) return new Result(sections.isEmpty() ? "UNAVAILABLE" : "AMBIGUOUS", List.of());
        List<String> paragraphs = sections.getFirst().select(".careers-listing-details__body-content p").stream()
                .map(Element::text).filter(s -> !s.isBlank()).toList();
        boolean hasSalary = paragraphs.stream().anyMatch(p -> p.matches("(?is).*(salary|compensation|earnings|pay range).*" )
                && p.matches("(?is).*(?:[$€£₹₩]|\\b(?:AUD|USD|CAD|EUR|GBP|KRW)\\b).*\\d.*"));
        return hasSalary ? new Result("PRESENT", paragraphs) : new Result("UNAVAILABLE", List.of());
    }

    private int[] countPostings(JsonNode node, String expected) {
        int matching = 0, total = 0;
        if (node == null) return new int[]{0, 0};
        if (node.isObject() && "JobPosting".equals(node.path("@type").asText())) {
            total++;
            if (expected.equals(node.path("identifier").path("value").asText())) matching++;
        } else if (node.isContainerNode()) {
            for (JsonNode child : node) {
                int[] counts = countPostings(child, expected);
                matching += counts[0]; total += counts[1];
            }
        }
        return new int[]{matching, total};
    }
}
