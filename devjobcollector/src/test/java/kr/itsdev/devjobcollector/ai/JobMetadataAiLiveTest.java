package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** One billable request using only synthetic job text. */
@EnabledIfEnvironmentVariable(named = "DJC_OPENAI_LIVE_TEST", matches = "true")
class JobMetadataAiLiveTest {
    @Test void modelReturnsGroundedExperienceAndLocationFromSyntheticPosting() {
        String input = "자격 요건: 백엔드 개발 실무 경력 3년 이상. 우대 사항: 머신러닝 7년 경험. 근무지: 서울 강남구.";
        var client = new OpenAiEvidenceClient(new ObjectMapper(), System.getenv("OPENAI_API_KEY"), true);
        var result = client.suggestJobMetadata(input);
        assertThat(result.path("experience").asText()).contains("3");
        assertThat(result.path("experienceQuote").asText()).isNotBlank().satisfies(quote -> assertThat(input).contains(quote));
        assertThat(result.path("location").asText()).contains("서울");
        assertThat(result.path("locationQuote").asText()).isNotBlank().satisfies(quote -> assertThat(input).contains(quote));
    }
}
