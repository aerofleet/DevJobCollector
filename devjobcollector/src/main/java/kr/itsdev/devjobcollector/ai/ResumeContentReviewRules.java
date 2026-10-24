package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.regex.Pattern;

/** Validate grounding independently from the model's structured-output promise. */
public final class ResumeContentReviewRules {
    public static final String VERSION = "content-review-v5-job-fit";
    public record Source(String id, String text, ResumeEvidenceReview.Document original) {}
    public record Job(int slot, long jobId, String text) {}
    public record Passage(String id, String text) {}
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");

    public static List<Passage> passages(String owner, String text) {
        var result = new ArrayList<Passage>();
        var sentences = Arrays.stream(text.split(Pattern.quote("[익명]")))
                .flatMap(part -> ResumeEvidenceReview.sentences(part).stream()).toList();
        for (String sentence : sentences) {
            int start = 0;
            while (start < sentence.length()) {
                int end = Math.min(start + 600, sentence.length());
                if (end < sentence.length()) {
                    int space = sentence.lastIndexOf(' ', end);
                    if (space > start) end = space;
                    else if (Character.isHighSurrogate(sentence.charAt(end - 1))) end--;
                }
                String quote = sentence.substring(start, end).strip();
                if (!quote.isEmpty()) result.add(new Passage(owner + "-P" + (result.size() + 1), quote));
                start = end;
                while (start < sentence.length() && Character.isWhitespace(sentence.charAt(start))) start++;
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, String> passageIndex(String owner, String text) {
        var result = new HashMap<String, String>();
        passages(owner, text).forEach(p -> result.put(p.id(), p.text()));
        return result;
    }

    public static Map<String, Object> schema() {
        return ResumeCoachingRules.schema();
    }
    static Map<String, Object> legacySchema() {
        var text = Map.of("type", "string");
        var finding = object(Map.of("sourceId", text, "quoteId", text,
                "issueType", Map.of("type", "string", "enum", List.of("CLARITY", "ROLE", "EVIDENCE", "RELEVANCE")),
                "problem", text, "actions", array(text, 3), "rewriteTemplate", text));
        var criterion = object(Map.of("jobQuoteId", text, "sourceId", text, "resumeQuoteId", text,
                "assessment", Map.of("type", "string", "enum", List.of("SUPPORTED", "CHECK")), "explanation", text));
        var job = object(Map.of("slot", Map.of("type", "integer", "minimum", 0, "maximum", 4),
                "relevance", Map.of("type", "string", "enum", List.of("RELATED", "REVIEW_NEEDED", "LOW_RELEVANCE")),
                "criteria", array(criterion, 4)));
        return object(Map.of("findings", array(finding, 6), "jobs", array(job, 5)));
    }
    private static Map<String, Object> object(Map<String, ?> fields) {
        return Map.of("type", "object", "additionalProperties", false, "required", new ArrayList<>(new TreeSet<>(fields.keySet())), "properties", fields);
    }
    private static Map<String, Object> array(Object item, int max) { return Map.of("type", "array", "maxItems", max, "items", item); }

    public static ObjectNode validate(JsonNode output, List<Source> sources, List<Job> jobs, ObjectMapper mapper) {
        if (output.has("rewrites")) return ResumeCoachingRules.validate(output, sources, jobs, mapper);
        return validateLegacy(output, sources, jobs, mapper);
    }
    static ObjectNode validateLegacy(JsonNode output, List<Source> sources, List<Job> jobs, ObjectMapper mapper) {
        if (!output.path("findings").isArray() || output.path("findings").size() > 6
                || !output.path("jobs").isArray() || output.path("jobs").size() > 5) throw new IllegalArgumentException("Invalid review");
        var bySource = new HashMap<String, Source>(); sources.forEach(s -> bySource.put(s.id(), s));
        var sourcePassages = new HashMap<String, Map<String, String>>();
        sources.forEach(s -> sourcePassages.put(s.id(), passageIndex(s.id(), s.text())));
        var result = mapper.createObjectNode(); result.put("version", VERSION);
        var findings = result.putArray("findings"); var dedup = new HashSet<String>();
        int discarded = 0;
        for (var item : output.path("findings")) {
            Source source = bySource.get(item.path("sourceId").asText());
            String quote = sourcePassages.getOrDefault(item.path("sourceId").asText(), Map.of())
                    .getOrDefault(bounded(item, "quoteId", 80), "");
            String type = item.path("issueType").asText();
            String problem = bounded(item, "problem", 600), rewrite = bounded(item, "rewriteTemplate", 1000);
            if (!grounded(source, quote) || !Set.of("CLARITY", "ROLE", "EVIDENCE", "RELEVANCE").contains(type)
                    || problem.isBlank() || rewrite.isBlank() || !item.path("actions").isArray() || item.path("actions").isEmpty() || item.path("actions").size() > 3) { discarded++; continue; }
            var actions = new ArrayList<String>();
            for (var action : item.path("actions")) if (action.isTextual() && !action.asText().isBlank() && action.asText().length() <= 400) actions.add(action.asText());
            if (actions.size() != item.path("actions").size() || introducesNumbers(problem + rewrite + String.join(" ", actions), source.text())) { discarded++; continue; }
            String key = "ai:" + type + ":" + ResumeAnalysisService.hash(quote);
            if (!dedup.add(key)) continue;
            var original = source.original();
            var finding = findings.addObject();
            finding.put("id", ResumeAnalysisService.hash(key).substring(0, 24)); finding.put("dedupKey", key);
            finding.put("feedback", "OPEN"); finding.put("source", "AI"); finding.put("issueType", type); finding.put("quote", quote);
            finding.put("problem", problem); finding.put("rewriteTemplate", rewrite); finding.set("actions", mapper.valueToTree(actions));
            finding.put("label", original.label()); finding.put("section", original.section());
            if (original.page() != null) finding.put("page", original.page());
            finding.put("sourcePath", original.key());
        }
        var validatedJobs = result.putArray("jobs"); var seen = new HashSet<Integer>();
        for (var item : output.path("jobs")) {
            if (!item.path("slot").isIntegralNumber()) continue;
            int slot = item.path("slot").asInt();
            Job job = jobs.stream().filter(j -> j.slot() == slot).findFirst().orElse(null);
            if (job == null || !seen.add(slot) || !Set.of("RELATED", "REVIEW_NEEDED", "LOW_RELEVANCE").contains(item.path("relevance").asText())) continue;
            var jobPassages = passageIndex("J" + slot, job.text());
            var criteria = mapper.createArrayNode();
            if (!item.path("criteria").isArray() || item.path("criteria").size() > 4) continue;
            for (var criterion : item.path("criteria")) {
                String jobQuote = jobPassages.getOrDefault(bounded(criterion, "jobQuoteId", 80), "");
                String quoteId = bounded(criterion, "resumeQuoteId", 80);
                String sourceId = bounded(criterion, "sourceId", 80);
                String quote = sourcePassages.getOrDefault(sourceId, Map.of()).getOrDefault(quoteId, "");
                String explanation = bounded(criterion, "explanation", 600), assessment = criterion.path("assessment").asText();
                Source source = bySource.get(criterion.path("sourceId").asText());
                // Missing evidence is explicit; forged IDs must not turn into an empty CHECK.
                if (quoteId.isBlank() != sourceId.isBlank() || (!quoteId.isBlank() && quote.isBlank())) continue;
                if (jobQuote.isBlank() || !job.text().contains(jobQuote) || explanation.isBlank()) continue;
                if (!Set.of("SUPPORTED", "CHECK").contains(assessment)) continue;
                if (assessment.equals("SUPPORTED") && !grounded(source, quote)) continue;
                if (!quote.isBlank() && !grounded(source, quote)) continue;
                if (introducesNumbers(explanation, job.text() + (source == null ? "" : source.text()))) continue;
                if (assessment.equals("SUPPORTED") && missingExplicitRequirement(jobQuote, quote)) {
                    assessment = "CHECK";
                    explanation = "인용된 경험만으로는 이 공고 요건 전체를 확인하기 어려워 추가 확인이 필요합니다.";
                }
                var saved = criteria.addObject(); saved.put("jobQuote", jobQuote); saved.put("resumeQuote", quote);
                saved.put("assessment", assessment); saved.put("explanation", explanation);
                if (source != null && !quote.isBlank()) {
                    saved.put("label", source.original().label()); saved.put("section", source.original().section());
                    if (source.original().page() != null) saved.put("page", source.original().page());
                }
            }
            if (criteria.isEmpty()) continue;
            boolean supported = false;
            for (var c : criteria) if (c.path("assessment").asText().equals("SUPPORTED")) supported = true;
            var saved = validatedJobs.addObject(); saved.put("jobId", job.jobId());
            String relevance = item.path("relevance").asText();
            saved.put("relevance", relevance.equals("RELATED") && !supported ? "REVIEW_NEEDED" : relevance);
            saved.set("criteria", criteria);
        }
        result.put("discardedFindings", discarded);
        return result;
    }
    private static boolean grounded(Source source, String quote) {
        return source != null && !quote.isBlank() && !quote.contains("[익명]")
                && source.text().contains(quote) && source.original().text().contains(quote);
    }
    private static boolean missingExplicitRequirement(String requirement, String evidence) {
        // Conservative lexical guard, not a semantic proof of eligibility. Match the cited
        // passage only: a technology elsewhere in the resume cannot support this criterion.
        if (introducesNumbers(requirement, evidence)) return true;
        var terms = Pattern.compile("[A-Za-z][A-Za-z0-9+#]*(?:[./-][A-Za-z0-9+#]+)*").matcher(requirement);
        while (terms.find()) {
            String term = terms.group();
            if (!Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(term) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE)
                    .matcher(evidence).find()) return true;
        }
        for (String activity : List.of("개발", "설계", "구현", "테스트", "운영", "배포", "분석", "최적화", "리팩토링", "주도", "관리"))
            if (requirement.contains(activity) && !evidence.contains(activity)) return true;
        return false;
    }
    private static String bounded(JsonNode item, String key, int max) {
        var value = item.path(key); return value.isTextual() && value.asText().length() <= max ? value.asText().strip() : "";
    }
    private static boolean introducesNumbers(String generated, String source) {
        var supported = new HashSet<String>(); NUMBER.matcher(source).results().forEach(m -> supported.add(m.group()));
        return NUMBER.matcher(generated).results().anyMatch(m -> !supported.contains(m.group()));
    }
    private ResumeContentReviewRules() {}
}
