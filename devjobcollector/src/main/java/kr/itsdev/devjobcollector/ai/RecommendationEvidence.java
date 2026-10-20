package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * External boundary: only server-defined enum values and ordinal slots.
 * Never serializes resume text, skill names, job text, identifiers or preferences.
 * This policy does not approve ResumePiiGate free-text output.
 */
public final class RecommendationEvidence {
    public static final String VERSION = "enum-evidence-v1";
    public enum Code {
        SKILL_MATCH("이력서에 작성한 기술과 공고의 기술 태그가 일치합니다."),
        CAREER_MATCH("입력한 경력 연수가 공고의 경력 조건과 일치합니다."),
        CATEGORY_MATCH("선택한 희망 직무가 공고의 직무와 일치합니다."),
        LOCATION_MATCH("선택한 희망 지역이 공고의 지역과 일치합니다."),
        EMPLOYMENT_MATCH("선택한 고용 형태가 공고의 고용 형태와 일치합니다."),
        RECENT_JOB("공고가 서비스에 등록된 지 30일 이내입니다. 원문 게시일과 다를 수 있습니다."),
        CHECK_MISSING_SKILLS("공고 기술 태그 중 이력서에서 확인되지 않은 항목을 확인해 주세요."),
        CHECK_CAREER("공고의 경력 조건을 자동으로 판별하지 못했습니다. 원문을 확인해 주세요.");
        public final String sentence;
        Code(String sentence) { this.sentence = sentence; }
    }
    public record Evidence(int slot, List<Code> evidence) {}
    public record Highlight(long jobId, List<Code> evidenceCodes, List<String> highlights) {}
    private RecommendationEvidence() {}

    public static List<Evidence> project(JsonNode analysis) {
        var jobs = analysis.path("recommendations");
        if (!jobs.isArray() || jobs.size() > 5) throw new IllegalArgumentException("Invalid recommendations");
        var output = new ArrayList<Evidence>();
        for (JsonNode job : jobs) {
            var codes = new ArrayList<Code>();
            var components = job.path("components");
            add(components, "skillExactMatch", Code.SKILL_MATCH, codes);
            add(components, "careerFit", Code.CAREER_MATCH, codes);
            add(components, "jobCategory", Code.CATEGORY_MATCH, codes);
            add(components, "locationFit", Code.LOCATION_MATCH, codes);
            add(components, "employmentType", Code.EMPLOYMENT_MATCH, codes);
            add(components, "jobFreshness", Code.RECENT_JOB, codes);
            if (job.path("missingPoints").isArray() && !job.path("missingPoints").isEmpty()) codes.add(Code.CHECK_MISSING_SKILLS);
            for (var unknown : job.path("unknownFactors")) {
                if (unknown.asText().equals("careerFit")) codes.add(Code.CHECK_CAREER);
            }
            output.add(new Evidence(output.size(), List.copyOf(codes)));
        }
        return List.copyOf(output);
    }

    private static void add(JsonNode node, String key, Code code, List<Code> codes) {
        JsonNode value = node.path(key);
        if (value.isNumber() && Double.isFinite(value.asDouble()) && value.asDouble() > 0) codes.add(code);
    }

    public static List<Highlight> validateAndRender(JsonNode output, List<Evidence> input, JsonNode analysis) {
        if (!output.isObject() || output.size() != 1 || !output.path("jobs").isArray()
                || output.path("jobs").size() != input.size()) throw new IllegalArgumentException("Invalid model output");
        var seen = new HashSet<Integer>();
        var result = new ArrayList<Highlight>();
        for (var job : output.path("jobs")) {
            if (!job.isObject() || job.size() != 2 || !job.path("slot").isIntegralNumber()
                    || !job.path("slot").canConvertToInt() || !job.path("reasons").isArray()) throw new IllegalArgumentException("Invalid slot");
            int slot = job.path("slot").asInt();
            if (slot < 0 || slot >= input.size() || !seen.add(slot)) throw new IllegalArgumentException("Invalid slot");
            var codes = new ArrayList<Code>();
            for (var reason : job.path("reasons")) {
                if (!reason.isTextual()) throw new IllegalArgumentException("Invalid reason");
                Code code = Code.valueOf(reason.asText());
                if (!input.get(slot).evidence().contains(code) || codes.contains(code)) throw new IllegalArgumentException("Unsupported evidence");
                codes.add(code);
            }
            if (codes.isEmpty() || codes.size() > 3) throw new IllegalArgumentException("Invalid reason count");
            result.add(new Highlight(analysis.path("recommendations").get(slot).path("jobId").asLong(),
                    List.copyOf(codes), codes.stream().map(code -> code.sentence).toList()));
        }
        return List.copyOf(result);
    }
}
