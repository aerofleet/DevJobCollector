package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in smoke tests. Synthetic data only; one billable API request per test. */
@EnabledIfEnvironmentVariable(named="DJC_OPENAI_LIVE_TEST",matches="true")
class OpenAiEvidenceLiveTest {
    @org.junit.jupiter.api.RepeatedTest(3)
    void nanoReviewsSyntheticResumeWithVerifiableQuotesAndJobRequirements() {
        var mapper = new ObjectMapper();
        var client = new OpenAiEvidenceClient(mapper, System.getenv("OPENAI_API_KEY"), true);
        String text = "팀 프로젝트에서 Java와 Spring Boot를 사용했습니다. 성능을 개선했습니다. 프로젝트에 참여했습니다.";
        String requirements = "Java와 Spring Boot 기반 API 개발 경험. 본인이 수행한 API 설계와 테스트 경험을 설명할 수 있는 분.";
        var document = new ResumeEvidenceReview.Document("/projects/0/description", "synthetic", null, "프로젝트 1", text);
        var sources = java.util.List.of(new ResumeContentReviewRules.Source("S1", text, document));
        var jobs = java.util.List.of(new ResumeContentReviewRules.Job(0, 1, requirements));
        var payload = java.util.Map.<String, Object>of("sources", java.util.List.of(java.util.Map.of("sourceId", "S1", "kind", "SAVED_RESUME", "section", "프로젝트 1", "field", "description", "text", text,
                        "passages", ResumeContentReviewRules.passages("S1", text))),
                "jobs", java.util.List.of(java.util.Map.of("slot", 0, "text", requirements, "passages", ResumeContentReviewRules.passages("J0", requirements))));
        var output = client.review(payload);
        var validated = ResumeContentReviewRules.validate(output, sources, jobs, mapper);
        assertThat(validated.path("findings").size()).as("Synthetic output: %s; validated: %s", output, validated).isGreaterThan(0);
        assertThat(validated.path("jobs").size()).as("Synthetic output: %s; validated: %s", output, validated).isEqualTo(1);
        for (var finding : validated.path("findings")) assertThat(text).contains(finding.path("quote").asText());
        for (var criterion : validated.path("jobs").get(0).path("criteria")) {
            assertThat(requirements).contains(criterion.path("jobQuote").asText());
            if (!criterion.path("resumeQuote").asText().isEmpty()) assertThat(text).contains(criterion.path("resumeQuote").asText());
        }
    }
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
