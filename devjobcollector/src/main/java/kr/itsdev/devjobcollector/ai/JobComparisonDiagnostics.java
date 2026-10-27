package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Counts and allowlisted reason codes only; never retain rejected model text. */
final class JobComparisonDiagnostics {
    static ObjectNode create(ObjectMapper mapper, long jobId, int sourcePassages, int jobPassages) {
        var value = mapper.createObjectNode();
        value.put("jobId", jobId).put("version", 1).put("receivedItems", 0)
                .put("submittedCriteria", 0).put("acceptedCriteria", 0).put("uncitedResumeCriteria", 0)
                .put("resumePassageCount", sourcePassages).put("jobPassageCount", jobPassages);
        value.putObject("issues");
        if (sourcePassages == 0) issue(value, "NO_RESUME_PASSAGES");
        if (jobPassages == 0) issue(value, "NO_JOB_PASSAGES");
        return value;
    }

    static void issue(ObjectNode value, String code) {
        var issues = (ObjectNode) value.path("issues");
        issues.put(code, issues.path(code).asInt() + 1);
    }

    static void finish(ObjectNode value) {
        String status = value.path("resumePassageCount").asInt() == 0 || value.path("jobPassageCount").asInt() == 0
                || value.path("issues").has("JOB_REQUIREMENTS_NOT_COLLECTED") ? "INPUT_MISSING"
                : value.path("receivedItems").asInt() == 0 ? "MODEL_OMITTED"
                : !value.path("issues").isEmpty() ? "VALIDATION_FAILED"
                : value.path("acceptedCriteria").asInt() == 0 ? "EMPTY_COMPARISON"
                : value.path("uncitedResumeCriteria").asInt() > 0 ? "RESUME_EVIDENCE_NOT_CITED" : "VALIDATED";
        value.put("status", status);
    }

    static String failureReason(ObjectNode value) {
        return switch (value.path("status").asText()) {
            case "INPUT_MISSING" -> "비교에 필요한 전송 자료가 부족합니다. 아래에 표시된 이력서 인용 구간 또는 공고 상세 요건의 수집 상태를 확인해 주세요.";
            case "MODEL_OMITTED" -> "AI 응답에서 이 공고의 비교 결과가 누락됐습니다. 등록 자료 부족으로 확인된 것은 아닙니다.";
            case "EMPTY_COMPARISON" -> "AI가 이 공고의 비교 항목을 작성하지 않았습니다. 등록 자료 부족으로 확인된 것은 아닙니다.";
            default -> "AI 비교 결과가 인용·판정 검증을 통과하지 못했습니다. 아래 검증 사유를 확인해 주세요.";
        };
    }

    private JobComparisonDiagnostics() {}
}
