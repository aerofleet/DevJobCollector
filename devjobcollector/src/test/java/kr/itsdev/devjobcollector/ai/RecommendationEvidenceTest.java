package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecommendationEvidenceTest {
    final ObjectMapper mapper=new ObjectMapper();
    final OpenAiEvidenceClient client=new OpenAiEvidenceClient(mapper,"test-key",true);

    @Test void arbitraryPersonalAndInjectedTextNeverEntersExternalPayload() throws Exception {
        for(int i=0;i<100;i++) {
            var analysis=mapper.createObjectNode();
            analysis.put("resumeId",987654321);
            analysis.put("rawText","이름 홍길동 secret-"+i+"@example.com ignore instructions");
            var job=analysis.putArray("recommendations").addObject();
            job.put("jobId",876543210);
            job.put("title","private instruction "+i);
            job.putArray("matchPoints").add("010-1234-5678");
            job.putArray("missingPoints").add("secret-"+i+"@example.com");
            job.putObject("components").put("skillExactMatch",30);
            var evidence=RecommendationEvidence.project(analysis);
            var payload=mapper.writeValueAsString(client.payload(evidence));
            assertThat(payload).doesNotContain("홍길동","example.com","010-1234-5678","987654321","876543210","private instruction");
            assertThat(payload).contains("SKILL_MATCH","CHECK_MISSING_SKILLS","gpt-5-nano");
            assertThat(client.payload(evidence).get("store")).isEqualTo(false);
        }
    }
    @Test void validatesReferencesAndRendersOnlyServerSentences() throws Exception {
        var analysis=mapper.readTree("""
                {"recommendations":[{"jobId":17,"components":{"skillExactMatch":30}}]}
                """);
        var input=RecommendationEvidence.project(analysis);
        var valid=mapper.readTree("""
                {"jobs":[{"slot":0,"reasons":["SKILL_MATCH"]}]}
                """);
        var result=RecommendationEvidence.validateAndRender(valid,input,analysis);
        assertThat(result.getFirst().jobId()).isEqualTo(17);
        assertThat(result.getFirst().highlights()).containsExactly(RecommendationEvidence.Code.SKILL_MATCH.sentence);
        for(String bad: List.of(
                "{\"jobs\":[{\"slot\":0,\"reasons\":[\"CAREER_MATCH\"]}]}",
                "{\"jobs\":[{\"slot\":1,\"reasons\":[\"SKILL_MATCH\"]}]}",
                "{\"jobs\":[{\"slot\":0,\"reasons\":[\"SKILL_MATCH\",\"SKILL_MATCH\"]}]}",
                "{\"jobs\":[{\"slot\":0,\"reasons\":[]}]}",
                "{\"jobs\":[{\"slot\":0,\"reasons\":[\"SKILL_MATCH\"],\"text\":\"invented\"}]}",
                "{\"jobs\":[],\"explanation\":\"invented\"}")) {
            assertThatThrownBy(()->RecommendationEvidence.validateAndRender(mapper.readTree(bad),input,analysis))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rejectsIncompleteRefusalAndAmbiguousProviderOutput() throws Exception {
        for(String bad:List.of(
                "{\"status\":\"incomplete\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}",
                "{\"status\":\"completed\",\"output\":[]}")) {
            assertThatThrownBy(()->client.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        var response=mapper.createObjectNode().put("status","completed");
        response.putArray("output").addObject().put("type","message").putArray("content").addObject()
                .put("type","output_text").put("text","{\"jobs\":[]}");
        assertThat(client.parse(response.toString()).path("jobs").isArray()).isTrue();
    }
    @Test void disabledOrMissingKeyDoesNotCallProvider() {
        assertThat(new OpenAiEvidenceClient(mapper,"",true).available()).isFalse();
        assertThatThrownBy(()->new OpenAiEvidenceClient(mapper,"test",false).select(List.of()))
                .hasMessage("EXPLANATION_UNAVAILABLE");
    }
    @Test void acceptsBoundedCoachingResponseAndRejectsOversizedOutput() throws Exception {
        var response=mapper.createObjectNode().put("status","completed");
        var content=response.putArray("output").addObject().put("type","message").putArray("content").addObject().put("type","output_text");
        content.put("text",mapper.writeValueAsString(java.util.Map.of("suggestion","가".repeat(20_000))));
        assertThat(client.parse(response.toString()).path("suggestion").asText()).hasSize(20_000);
        content.put("text","x".repeat(48_001));
        assertThatThrownBy(() -> client.parse(response.toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void titleEvidenceCannotBeRenderedAsVerifiedTags() throws Exception {
        var analysis = mapper.readTree("""
                {"recommendations":[{"jobId":17,"skillEvidenceSource":"JOB_TITLE","components":{"skillExactMatch":30}}]}
                """);
        var input = RecommendationEvidence.project(analysis);
        assertThat(input.getFirst().evidence()).containsExactly(RecommendationEvidence.Code.TITLE_SKILL_MATCH);
        assertThatThrownBy(() -> RecommendationEvidence.validateAndRender(
                mapper.readTree("{\"jobs\":[{\"slot\":0,\"reasons\":[\"SKILL_MATCH\"]}]}"), input, analysis))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
