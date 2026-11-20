package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JobMetadataAiPayloadTest {
    @Test void jobRolePayloadUsesAllowedModelAndUntrustedInputRules() {
        var client = new OpenAiEvidenceClient(new ObjectMapper(), "", false);
        var payload = new ObjectMapper().valueToTree(client.jobRolePayload("Selection Operations Manager", "Retail Selection", "Lead operators and improve selection workflows."));
        assertThat(payload.path("model").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(payload.path("store").asBoolean()).isFalse();
        assertThat(payload.path("max_output_tokens").asInt()).isEqualTo(300);
        assertThat(payload.path("instructions").asText()).contains("untrusted data", "ABSTAIN");
        assertThat(payload.path("text").path("format").path("schema").path("required").size()).isEqualTo(3);
        assertThatThrownBy(() -> client.jobRolePayload("Manager", "Retail", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.jobRolePayload("Manager", "Retail", "x".repeat(4001))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void usesStructuredOutputAndLimitsInputWithoutInvokingProvider() {
        var client = new OpenAiEvidenceClient(new ObjectMapper(), "", false);
        var payload = new ObjectMapper().valueToTree(client.jobMetadataPayload("자격요건: 경력 3년 이상"));
        assertThat(payload.path("store").asBoolean()).isFalse();
        assertThat(payload.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(payload.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(payload.path("text").path("format").path("schema").path("required").size()).isEqualTo(4);
        assertThatThrownBy(() -> client.jobMetadataPayload(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.jobMetadataPayload("a".repeat(12001))).isInstanceOf(IllegalArgumentException.class);
    }
}
