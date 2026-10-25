package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;

/** Grounded coaching, with conditional alternatives kept separate until the owner answers. */
final class ResumeCoachingRules {
    private static final Map<String, String> TEXT = Map.of("type", "string");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");
    private static Map<String, Object> object(Map<String, ?> fields) {
        return Map.of("type", "object", "additionalProperties", false, "required", new ArrayList<>(fields.keySet()), "properties", fields);
    }
    private static Map<String, Object> array(Object item, int max) { return Map.of("type", "array", "items", item, "maxItems", max); }
    private static Map<String, Object> ordered(Object... pairs) {
        var fields = new LinkedHashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) fields.put((String)pairs[i],pairs[i+1]);
        return fields;
    }
    static Map<String, Object> schema() {
        var ref = object(ordered("sourceId", TEXT, "quoteId", TEXT));
        var refs = array(ref, 5);
        var option = object(ordered("label", TEXT, "suggestion", TEXT));
        var confirm = object(ordered("question", TEXT, "options", array(option, 3)));
        var rewrite = object(ordered("sourceId", TEXT, "quoteId", TEXT, "evidence", refs,
                "suggestion", TEXT, "reason", TEXT, "confirm", array(confirm, 1)));
        var strength = object(ordered("evidence", refs, "point", TEXT, "placement", TEXT));
        var gap = object(Map.of("type", Map.of("type", "string", "enum", List.of("missing", "weak", "buried")),
                "what", TEXT, "why_it_matters", TEXT, "evidence", refs));
        // Keep the previously verified per-job quote contract and add actionable appeal copy.
        var criterion = object(Map.of("jobQuoteId", TEXT, "sourceId", TEXT, "resumeQuoteId", TEXT,
                "assessment", Map.of("type", "string", "enum", List.of("SUPPORTED", "CHECK")), "explanation", TEXT));
        var job = object(Map.of("slot", Map.of("type", "integer", "minimum", 0, "maximum", 4),
                "relevance", Map.of("type", "string", "enum", List.of("RELATED", "REVIEW_NEEDED", "LOW_RELEVANCE")),
                "decision", Map.of("type", "string", "enum", List.of("RECOMMENDED", "STRETCH", "NOT_RECOMMENDED", "INSUFFICIENT_INFORMATION")),
                "decisionReason", TEXT,
                "lead_with", TEXT, "gap", TEXT, "criteria", array(criterion, 4)));
        return object(ordered("positioningEvidence", refs, "positioning", TEXT, "strengths", array(strength, 3),
                "gaps", array(gap, 6), "rewrites", array(rewrite, 6), "job_fit", array(job, 5)));
    }
    private record Evidence(String text, ArrayNode quotes) {}
    private static Evidence evidence(JsonNode refs, List<ResumeContentReviewRules.Source> sources, ObjectMapper mapper) {
        var quotes = mapper.createArrayNode(); var text = new StringBuilder();
        if (!refs.isArray() || refs.isEmpty() || refs.size() > 5) return new Evidence("", quotes);
        for (var ref : refs) {
            var source = sources.stream().filter(s -> s.id().equals(ref.path("sourceId").asText())).findFirst().orElse(null);
            if (source == null) return new Evidence("", mapper.createArrayNode());
            var passage = ResumeContentReviewRules.passages(source.id(), source.text()).stream()
                    .filter(p -> p.id().equals(ref.path("quoteId").asText())).findFirst().orElse(null);
            if (passage == null || !source.original().text().contains(passage.text())) return new Evidence("", mapper.createArrayNode());
            text.append(passage.text()).append('\n');
            var q = quotes.addObject(); q.put("quote", passage.text()); q.put("label", source.original().label());
            q.put("section", source.original().section()); q.put("sourcePath", source.original().key());
            if (source.original().page() != null) q.put("page", source.original().page());
        }
        return new Evidence(text.toString(), quotes);
    }
    private static String text(JsonNode node, String key) {
        var v = node.path(key); return v.isTextual() && v.asText().length() <= 1500 ? v.asText().strip() : "";
    }
    private static boolean valid(String value, String evidence) {
        if (value.isBlank() || value.contains("[") || value.contains("]") || value.contains("【") || value.contains("__")) return false;
        var numbers = new HashSet<String>(); NUMBER.matcher(evidence).results().forEach(m -> numbers.add(m.group()));
        return NUMBER.matcher(value).results().allMatch(m -> numbers.contains(m.group()));
    }
    private static boolean groundedClaim(String value, String evidence) {
        if (!valid(value,evidence)) return false;
        // Job requirements must never become the applicant's skills or responsibilities.
        for (String claim : List.of("단독","주도","설계","테스트","리팩토링","수상","최우수"))
            if (value.contains(claim) && !evidence.contains(claim)) return false;
        return Pattern.compile("(?<![A-Za-z])[A-Z][A-Za-z0-9+#]*(?:\\.[A-Za-z]+)?")
                .matcher(value).results().allMatch(m -> evidence.toLowerCase(Locale.ROOT).contains(m.group().toLowerCase(Locale.ROOT)));
    }
    private static String readableReason(String value, JsonNode refs) {
        // Normalize only references already checked against the applicant's source passages.
        var identifiers = new TreeSet<String>(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        for (var ref : refs) {
            identifiers.add(ref.path("sourceId").asText());
            identifiers.add(ref.path("quoteId").asText());
        }
        for (String id : identifiers) {
            if (!id.isBlank()) value = value.replaceAll("(?<![A-Za-z0-9_-])" + Pattern.quote(id) + "(?![A-Za-z0-9_-])", "원문");
        }
        // Unknown references must not become ordinary prose or evade the numeric check.
        return Pattern.compile("[SJ][0-9]+(?:-P[0-9]+)?").matcher(value).find() ? "" : value;
    }
    static ObjectNode validate(JsonNode output, List<ResumeContentReviewRules.Source> sources,
            List<ResumeContentReviewRules.Job> jobs, ObjectMapper mapper) {
        for (String field : List.of("strengths", "gaps", "rewrites", "job_fit"))
            if (!output.path(field).isArray() || output.path(field).size() > (field.equals("strengths") ? 3 : field.equals("job_fit") ? 5 : 6))
                throw new IllegalArgumentException("Invalid coaching output");
        var legacy = mapper.createObjectNode(); legacy.putArray("findings"); legacy.set("jobs", output.path("job_fit"));
        var result = ResumeContentReviewRules.validateLegacy(legacy, sources, jobs, mapper);
        var positioning = evidence(output.path("positioningEvidence"), sources, mapper);
        String headline = text(output, "positioning");
        result.put("positioning", !positioning.text().isBlank() && groundedClaim(headline, positioning.text()) ? headline : "");
        result.set("positioningEvidence", positioning.quotes());
        var strengths = result.putArray("strengths");
        for (var item : output.path("strengths")) {
            var e = evidence(item.path("evidence"), sources, mapper);
            if (e.text().isBlank() || !groundedClaim(text(item,"point"), e.text()) || !valid(text(item,"placement"), e.text())) continue;
            var saved = strengths.addObject(); saved.put("point", text(item,"point")); saved.put("placement", text(item,"placement")); saved.set("evidence", e.quotes());
        }
        var gaps = result.putArray("gaps");
        for (var item : output.path("gaps")) {
            var e = evidence(item.path("evidence"), sources, mapper);
            if (!Set.of("missing","weak","buried").contains(text(item,"type")) || e.text().isBlank()
                    || !valid(text(item,"what"), e.text()) || !valid(text(item,"why_it_matters"), e.text())) continue;
            var saved = gaps.addObject(); saved.put("type", text(item,"type")); saved.put("what", text(item,"what"));
            saved.put("why_it_matters", text(item,"why_it_matters")); saved.set("evidence", e.quotes());
        }
        var findings = result.withArray("findings"); var seen = new HashSet<String>(); int discarded = 0;
        for (var item : output.path("rewrites")) {
            var primary = mapper.createArrayNode(); primary.addObject().put("sourceId", text(item,"sourceId")).put("quoteId", text(item,"quoteId"));
            var original = evidence(primary, sources, mapper); var e = evidence(item.path("evidence"), sources, mapper);
            String facts = original.text() + e.text(), suggestion = text(item,"suggestion"), reason = text(item,"reason");
            var checkedRefs = mapper.createArrayNode();
            if (!original.text().isBlank()) checkedRefs.addAll(primary);
            if (!e.text().isBlank()) checkedRefs.addAll((ArrayNode)item.path("evidence"));
            reason = readableReason(reason, checkedRefs);
            if (original.text().isBlank() || !groundedClaim(suggestion,facts) || !valid(reason,facts)
                    || !item.path("confirm").isArray() || item.path("confirm").size() > 1) { discarded++; continue; }
            String key = "ai:rewrite:" + ResumeAnalysisService.hash(original.text()); if (!seen.add(key)) continue;
            var saved = findings.addObject(); saved.setAll((ObjectNode) original.quotes().get(0));
            saved.put("id", ResumeAnalysisService.hash(key).substring(0,24)); saved.put("dedupKey",key);
            saved.put("source","AI"); saved.put("feedback","OPEN"); saved.put("issueType","CLARITY");
            saved.put("problem",reason); saved.put("rewriteTemplate",suggestion); saved.put("suggestion",suggestion);
            saved.put("baseSuggestion",suggestion); saved.putArray("actions").add(reason); saved.set("evidence",e.quotes());
            var confirmations = saved.putArray("confirm");
            for (var c : item.path("confirm")) {
                if (!valid(text(c,"question"), facts) || !c.path("options").isArray() || c.path("options").size()<2 || c.path("options").size()>3) continue;
                var options = mapper.createArrayNode(); var labels = new HashSet<String>();
                for (var option : c.path("options")) {
                    String label = text(option,"label"), variant = text(option,"suggestion");
                    if (label.length() > 100 || !valid(label, facts) || !groundedClaim(variant, facts + " " + label) || !labels.add(label)) continue;
                    options.addObject().put("label",label).put("suggestion",variant);
                }
                if (options.size() != c.path("options").size()) continue;
                confirmations.addObject().put("question",text(c,"question")).set("options",options);
            }
        }
        for (var saved : result.path("jobs")) {
            var job = jobs.stream().filter(j -> j.jobId()==saved.path("jobId").asLong()).findFirst().orElseThrow();
            var item = java.util.stream.StreamSupport.stream(output.path("job_fit").spliterator(),false)
                    .filter(j -> j.path("slot").asInt(-1)==job.slot()).findFirst().orElseThrow();
            StringBuilder facts = new StringBuilder(), resumeFacts = new StringBuilder();
            saved.path("criteria").forEach(c -> { facts.append(c.path("resumeQuote").asText()).append(' ').append(c.path("jobQuote").asText()); resumeFacts.append(c.path("resumeQuote").asText()).append(' '); });
            boolean supported = java.util.stream.StreamSupport.stream(saved.path("criteria").spliterator(),false)
                    .anyMatch(c -> c.path("assessment").asText().equals("SUPPORTED"));
            String decision = text(item, "decision"), decisionReason = text(item, "decisionReason");
            boolean completeEvidence = saved.path("criteria").size() == item.path("criteria").size();
            if (Set.of("RECOMMENDED", "STRETCH", "NOT_RECOMMENDED", "INSUFFICIENT_INFORMATION").contains(decision)) {
                boolean hasApplicantEvidence = java.util.stream.StreamSupport.stream(saved.path("criteria").spliterator(), false)
                        .anyMatch(c -> !c.path("resumeQuote").asText().isBlank());
                if (!completeEvidence || !valid(decisionReason, facts.toString())
                        || ((decision.equals("RECOMMENDED") || decision.equals("STRETCH")) && !supported)
                        || (decision.equals("NOT_RECOMMENDED") && !hasApplicantEvidence)) {
                    decision = "INSUFFICIENT_INFORMATION";
                    decisionReason = "판정 근거를 충분히 확인하지 못해 추가 검토가 필요합니다.";
                }
                ((ObjectNode)saved).put("decision", decision).put("decisionReason", decisionReason);
            }
            for (String field : List.of("lead_with","gap")) {
                String value = text(item,field); ((ObjectNode)saved).put(field, field.equals("lead_with")
                        ? (supported && groundedClaim(value,resumeFacts.toString()) ? value : "") : (valid(value,facts.toString()) ? value : ""));
            }
        }
        boolean hasDecisions = java.util.stream.StreamSupport.stream(output.path("job_fit").spliterator(), false).anyMatch(j -> j.has("decision"));
        if (hasDecisions) {
            var included = new HashSet<Long>(); result.path("jobs").forEach(j -> included.add(j.path("jobId").asLong()));
            for (var job : jobs) if (!included.contains(job.jobId())) {
                ((ArrayNode)result.path("jobs")).addObject().put("jobId", job.jobId()).put("relevance", "REVIEW_NEEDED")
                        .put("decision", "INSUFFICIENT_INFORMATION").put("decisionReason", "AI가 충분한 비교 근거를 제시하지 않아 추천을 보류했습니다.")
                        .putArray("criteria");
            }
        }
        result.put("discardedFindings",discarded); return result;
    }
    static void answer(ObjectNode review, String findingId, int option) {
        var finding = java.util.stream.StreamSupport.stream(review.path("findings").spliterator(), false)
                .filter(f -> f.path("id").asText().equals(findingId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown rewrite"));
        if (finding.path("confirm").size()!=1 || option < -1 || option >= finding.path("confirm").path(0).path("options").size())
            throw new IllegalArgumentException("Unknown option");
        String suggestion = option == -1 ? finding.path("baseSuggestion").asText() : finding.path("confirm").path(0).path("options").path(option).path("suggestion").asText();
        ((ObjectNode)finding).put("selectedOption",option).put("suggestion",suggestion).put("rewriteTemplate",suggestion);
    }
    private ResumeCoachingRules() {}
}
