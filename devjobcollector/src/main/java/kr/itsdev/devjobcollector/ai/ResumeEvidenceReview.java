package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;

/** Local observations, not a hiring score. Full source documents are transient. */
public final class ResumeEvidenceReview {
    public static final String VERSION = "verified-quotes-v1";
    private static final Pattern OUTCOME = Pattern.compile("개선|최적화|단축|향상|감소|증가|improv|optimi|reduc", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEASUREMENT = Pattern.compile("\\d+(?:[.,]\\d+)?\\s*(?:%|ms\\b|초|분|시간|건|명|배|MB\\b|GB\\b|TPS\\b|RPS\\b)", Pattern.CASE_INSENSITIVE);
    public record Document(String key, String label, Integer page, String section, String text) {}
    public record Location(String sourceKey, String label, Integer page, String section) {}
    public record Proposal(String rule, String quote, String problem, String rewriteTemplate, Document document) {}
    public record Finding(String id, String dedupKey, String priority, String category, String source,
                          List<Location> locations, String quote, String problem, String rewriteTemplate, String feedback) {}

    public static List<Document> savedDocuments(JsonNode content) {
        var result = new ArrayList<Document>();
        for (String field : List.of("projects", "experience", "techStack")) collect(content.path(field), "/" + field, field, result);
        return result;
    }

    private static void collect(JsonNode node, String path, String section, List<Document> documents) {
        if (path.chars().filter(c -> c == '/').count() > 14) return;
        if (node.isTextual() && !node.asText().isBlank()) documents.add(new Document(path, "저장된 이력서", null, section, node.asText()));
        else if (node.isArray()) { for (int i = 0; i < node.size(); i++) collect(node.get(i), path + "/" + i, section, documents); }
        else if (node.isObject()) node.fields().forEachRemaining(entry -> collect(entry.getValue(), path + "/" + entry.getKey(), section, documents));
    }

    public static Map<String, Object> review(List<Document> documents, Set<String> skills) {
        var proposals = new ArrayList<Proposal>();
        int outcomes = 0, measured = 0;
        for (Document document : documents) {
            for (String line : document.text().split("(?<=[.!?。])\\s+|[\\r\\n]+")) {
                String quote = line.strip();
                if (!OUTCOME.matcher(quote).find()) continue;
                outcomes++;
                if (MEASUREMENT.matcher(quote).find()) { measured++; continue; }
                // Do not truncate a sentence into a misleading quotation.
                if (quote.length() < 6 || quote.length() > 600) continue;
                proposals.add(new Proposal("measurement_not_detected", quote,
                        "성과 표현이 있지만 이 문장에서 측정값과 단위를 찾지 못했습니다. 주변 문맥에 근거가 있는지 확인해 주세요.",
                        "[측정 조건]에서 [지표]를 [측정 전 값·단위] → [측정 후 값·단위]로 변경했습니다. 측정 자료가 없다면 수행한 작업과 확인 방법만 작성하세요.", document));
            }
        }
        var evidence = new TreeMap<String, List<Location>>();
        var vocabulary = new ArrayList<>(skills);
        vocabulary.addAll(List.of("Spring Boot", "스프링부트", "Node.js", "React.js", "JS", "TS", "Postgres"));
        for (Document document : documents) {
            for (String skill : SavedResumeAnalysisInput.findSkills(document.text(), vocabulary)) {
                if (skills.contains(skill)) evidence.computeIfAbsent(skill, ignored -> new ArrayList<>()).add(location(document));
            }
        }
        evidence.replaceAll((skill, locations) -> locations.stream().distinct().limit(10).toList());
        return Map.of("version", VERSION, "findings", validate(proposals, documents), "skillEvidence", evidence,
                "score", Map.of("status", "NOT_SCORED", "reason", "검증된 종합 평가 기준이 없어 점수를 산출하지 않습니다."),
                "observations", Map.of("outcomeSentences", outcomes, "withMeasurement", measured,
                        "basis", "개선·최적화 등 성과 표현이 포함된 추출 문장 중 숫자와 단위가 함께 있는 문장을 셉니다. 중복 자료도 포함되며, 수치의 진위나 성과의 우수성을 평가하지 않습니다."));
    }

    /** Reject unknown sources and non-verbatim quotes before persisting any finding. */
    static List<Finding> validate(List<Proposal> proposals, List<Document> documents) {
        var trusted = new HashSet<>(documents);
        var findings = new LinkedHashMap<String, Finding>();
        for (Proposal proposal : proposals) {
            if (!trusted.contains(proposal.document()) || proposal.quote() == null || proposal.quote().isBlank()
                    || proposal.quote().length() > 600 || !proposal.document().text().contains(proposal.quote())) continue;
            String key = proposal.rule() + ":" + ResumeAnalysisService.hash(proposal.quote());
            var old = findings.get(key);
            if (old == null && findings.size() >= 20) continue;
            var locations = new ArrayList<Location>();
            if (old != null) locations.addAll(old.locations());
            Location location = location(proposal.document());
            if (!locations.contains(location)) locations.add(location);
            findings.put(key, new Finding(ResumeAnalysisService.hash(key).substring(0, 24), key, "review", "evidence", "RULE",
                    List.copyOf(locations), proposal.quote(), proposal.problem(), proposal.rewriteTemplate(), "OPEN"));
        }
        return List.copyOf(findings.values());
    }

    private static Location location(Document document) {
        return new Location(document.key(), document.label(), document.page(), document.section());
    }

    private ResumeEvidenceReview() {}
}
