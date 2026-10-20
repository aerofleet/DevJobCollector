package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in smoke test. Synthetic enum evidence only; one billable API request. */
@EnabledIfEnvironmentVariable(named="DJC_OPENAI_LIVE_TEST",matches="true")
class OpenAiEvidenceLiveTest {
    @Test void nanoReturnsSupportedEvidenceForSyntheticJobs() throws Exception {
        var mapper=new ObjectMapper();
        var client=new OpenAiEvidenceClient(mapper,System.getenv("OPENAI_API_KEY"),true);
        assertThat(client.available()).isTrue();
        var analysis=mapper.readTree("""
                {"recommendations":[
                  {"jobId":1,"components":{"skillExactMatch":30,"careerFit":15},"missingPoints":["synthetic"]},
                  {"jobId":2,"components":{"jobCategory":10},"unknownFactors":["careerFit"]}
                ]}
                """);
        var evidence=RecommendationEvidence.project(analysis);
        var output=client.select(evidence);
        assertThat(RecommendationEvidence.validateAndRender(output,evidence,analysis)).hasSize(2);
    }
}
