package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JobFieldAiPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void supports_multiple_grounded_fields_and_abstention() throws Exception {
        var output = mapper.readTree("""
                {"fields":[{"field":"전산직","evidence":"시스템 개발"},{"field":"연구직","evidence":"연구 업무"}]}
                """);
        assertThat(JobFieldAiPolicy.validate(output, "시스템 개발 및 연구 업무")).extracting(JobFieldAiPolicy.Field::field)
                .containsExactly("전산직", "연구직");
        assertThat(JobFieldAiPolicy.validate(mapper.readTree("{\"fields\":[]}"), "본문")).isEmpty();
    }
    @Test void rejects_unknown_fields_unsupported_quotes_duplicates_and_mixed_other() throws Exception {
        for (String output : new String[] {
                "{\"fields\":[{\"field\":\"개발직\",\"evidence\":\"시스템\"}]}",
                "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"허위\"}]}",
                "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"시스템\"},{\"field\":\"전산직\",\"evidence\":\"시스템\"}]}",
                "{\"fields\":[{\"field\":\"계약직\",\"evidence\":\"시스템\"}]}",
                "{\"fields\":[{\"field\":\"기타\",\"evidence\":\"시스템\"},{\"field\":\"전산직\",\"evidence\":\"시스템\"}]}"
        }) assertThatThrownBy(() -> JobFieldAiPolicy.validate(mapper.readTree(output), "시스템 개발"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void request_contains_only_the_fixed_taxonomy_and_bounded_model_settings() {
        var client = new OpenAiEvidenceClient(mapper, "", false);
        var payload = client.jobFieldPayload("전산 연구원", "정보통신", "시스템 개발");
        assertThat(payload).containsEntry("model", "gpt-5.4-mini").containsEntry("store", false).containsEntry("max_output_tokens", 400);
        assertThat(payload.toString()).contains("전산직", "행정직", "기타", "계약직");
        assertThat(payload.get("instructions").toString()).contains("exact contiguous substring", "Do not paraphrase", "기간제");
        assertThatThrownBy(() -> client.jobFieldPayload("", "", "본문")).hasMessage("INVALID_JOB_FIELD_INPUT");
    }
}
