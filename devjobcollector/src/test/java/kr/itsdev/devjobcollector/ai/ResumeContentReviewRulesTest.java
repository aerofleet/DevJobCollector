package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResumeContentReviewRulesTest {
    final ObjectMapper mapper = new ObjectMapper();
    final String quote = "Java API를 개발했습니다.";
    final ResumeEvidenceReview.Document doc = new ResumeEvidenceReview.Document("/projects/0/description", "저장된 이력서", null, "프로젝트 1", quote);
    final List<ResumeContentReviewRules.Source> sources = List.of(new ResumeContentReviewRules.Source("S1", quote, doc));
    @Test void rejectsInventedQuotesNumbersAndSourcesAndDeduplicates() throws Exception {
        var response = mapper.readTree("""
            {"findings":[
             {"sourceId":"S1","quote":"Java API를 개발했습니다.","issueType":"ROLE","problem":"담당 범위를 설명하세요.","actions":["직접 구현한 기능을 적으세요."],"rewriteTemplate":"[담당 기능]을 구현했습니다."},
             {"sourceId":"S1","quote":"Java API를 개발했습니다.","issueType":"ROLE","problem":"담당 범위를 설명하세요.","actions":["직접 구현한 기능을 적으세요."],"rewriteTemplate":"[담당 기능]을 구현했습니다."},
             {"sourceId":"S1","quote":"Java API를 개발했습니다.","issueType":"EVIDENCE","problem":"성과 설명","actions":["측정하세요"],"rewriteTemplate":"응답 시간을 180ms로 개선했습니다."},
             {"sourceId":"S1","quote":"없는 문장","issueType":"ROLE","problem":"설명","actions":["수정"],"rewriteTemplate":"[역할]"},
             {"sourceId":"FAKE","quote":"Java API를 개발했습니다.","issueType":"ROLE","problem":"설명","actions":["수정"],"rewriteTemplate":"[역할]"}],"jobs":[]}
            """);
        var result = ResumeContentReviewRules.validate(response, sources, List.of(), mapper);
        assertThat(result.path("findings").size()).isEqualTo(1);
        assertThat(result.path("discardedFindings").asInt()).isEqualTo(3);
        assertThat(result.toString()).doesNotContain("180ms", "없는 문장");
    }
    @Test void onlyKeepsSuppliedJobsAndVerbatimRequirementsAndResumeQuotes() throws Exception {
        var response = mapper.readTree("""
            {"findings":[],"jobs":[
             {"slot":0,"relevance":"RELATED","criteria":[
              {"jobQuote":"Java API 개발","sourceId":"S1","resumeQuote":"Java API를 개발했습니다.","assessment":"SUPPORTED","explanation":"API 개발 경험을 연결해 볼 수 있습니다."},
              {"jobQuote":"Kubernetes 필수","sourceId":"S1","resumeQuote":"Java API를 개발했습니다.","assessment":"SUPPORTED","explanation":"일치"}]},
             {"slot":4,"relevance":"RELATED","criteria":[]}]}
            """);
        var result = ResumeContentReviewRules.validate(response, sources, List.of(new ResumeContentReviewRules.Job(0, 17, "Java API 개발")), mapper);
        assertThat(result.path("jobs").size()).isEqualTo(1);
        assertThat(result.path("jobs").get(0).path("jobId").asLong()).isEqualTo(17);
        assertThat(result.path("jobs").get(0).path("criteria").size()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("Kubernetes");
    }
    @Test void payloadUsesStructuredOutputWithoutToolsOrStorage() {
        var client = new OpenAiEvidenceClient(mapper, "test", true);
        var payload = mapper.valueToTree(client.reviewPayload(Map.of("sources", List.of(), "jobs", List.of())));
        assertThat(payload.path("store").asBoolean()).isFalse();
        assertThat(payload.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(payload.has("tools")).isFalse();
        assertThat(payload.path("instructions").asText()).contains("UNTRUSTED DATA", "Never invent");
    }
}
