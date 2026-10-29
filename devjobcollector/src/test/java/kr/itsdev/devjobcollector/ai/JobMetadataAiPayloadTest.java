package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JobMetadataAiPayloadTest {
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
