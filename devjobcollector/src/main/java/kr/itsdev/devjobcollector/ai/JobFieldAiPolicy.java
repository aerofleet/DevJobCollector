package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed public job fields; quoted evidence is necessary but not proof of semantic correctness. */
public final class JobFieldAiPolicy {
    public static final String VERSION = "job-field-v1";
    public static final List<String> FIELDS = List.of("일반직", "행정직", "경영직", "사무직", "관리직", "연구직", "기술직", "시설직",
            "전산직", "교수직", "의사직", "간호직", "약무직", "보건직", "위생직", "기능직", "운영직", "공무직", "안전직",
            "교원직", "별정직", "계약직", "전문직", "상담직", "기타");
    private JobFieldAiPolicy() {}

    /** Discard only contract items whose own quote lacks explicit contract evidence. */
    public static com.fasterxml.jackson.databind.node.ObjectNode omitUnsupportedContractQuote(JsonNode response, String source) {
        if (response == null || !response.isObject() || response.size() != 1 || !response.path("fields").isArray()
                || response.path("fields").size() > 3) throw new IllegalArgumentException("INVALID_JOB_FIELD_OUTPUT");
        var filtered = ((com.fasterxml.jackson.databind.node.ObjectNode) response).deepCopy();
        var fields = (com.fasterxml.jackson.databind.node.ArrayNode) filtered.path("fields");
        for (int i = fields.size() - 1; i >= 0; i--) {
            JsonNode item = fields.get(i);
            if (!item.isObject() || item.size() != 2 || !item.path("field").isTextual() || !item.path("evidence").isTextual())
                throw new IllegalArgumentException("INVALID_JOB_FIELD_OUTPUT");
            String evidence = item.path("evidence").asText();
            if ("계약직".equals(item.path("field").asText()) && !evidence.isBlank() && evidence.length() <= 300
                    && source != null && source.contains(evidence)
                    && !java.util.regex.Pattern.compile("계약직|기간제|\\bcontract(?:ual)?\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(evidence).find())
                fields.remove(i);
        }
        return filtered;
    }

    public static List<Field> validate(JsonNode response, String source) {
        if (source == null || response == null || !response.isObject() || response.size() != 1
                || !response.path("fields").isArray() || response.path("fields").size() > 3)
            throw new IllegalArgumentException("INVALID_JOB_FIELD_OUTPUT");
        var result = new ArrayList<Field>();
        var seen = new HashSet<String>();
        for (var item : response.path("fields")) {
            if (!item.isObject() || item.size() != 2 || !item.path("field").isTextual() || !item.path("evidence").isTextual())
                throw new IllegalArgumentException("INVALID_JOB_FIELD_OUTPUT");
            String field = item.path("field").asText();
            String evidence = item.path("evidence").asText();
            if (!FIELDS.contains(field) || !seen.add(field)) throw new IllegalArgumentException("INVALID_JOB_FIELD_LABEL");
            if (evidence.isBlank() || evidence.length() > 300) throw new IllegalArgumentException("INVALID_JOB_FIELD_QUOTE_LENGTH");
            if (!source.contains(evidence)) throw new IllegalArgumentException("INVALID_JOB_FIELD_QUOTE_SOURCE");
            if ("계약직".equals(field) && !java.util.regex.Pattern.compile("계약직|기간제|\\bcontract(?:ual)?\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(evidence).find())
                throw new IllegalArgumentException("INVALID_JOB_FIELD_CONTRACT_QUOTE");
            if (("공무직".equals(field) || "별정직".equals(field)) && !evidence.contains(field))
                throw new IllegalArgumentException("INVALID_JOB_FIELD_APPOINTMENT_QUOTE");
            result.add(new Field(field, evidence));
        }
        if (seen.contains("기타") && seen.size() > 1) throw new IllegalArgumentException("INVALID_JOB_FIELD_OUTPUT");
        return List.copyOf(result);
    }

    public record Field(String field, String evidence) {}
}
