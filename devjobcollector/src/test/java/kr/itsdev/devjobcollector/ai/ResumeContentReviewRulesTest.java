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
    @Test void reconstructsOriginalTextEvenWhenModelAddsInventedQuoteFields() throws Exception {
        var output = mapper.readTree("""
                {"findings":[{"sourceId":"S1","quoteId":"S1-P1","quote":"프로젝트 1: 위조 인용",
                "issueType":"ROLE","problem":"역할을 설명하세요.","actions":["담당 기능을 적으세요."],"rewriteTemplate":"[담당 기능]"}],
                "jobs":[{"slot":0,"relevance":"RELATED","criteria":[{"jobQuoteId":"J0-P1","sourceId":"S1","resumeQuoteId":"S1-P1",
                "jobQuote":"위조 요건","resumeQuote":"따옴표를 추가한 문장","assessment":"SUPPORTED","explanation":"경험이 언급됐습니다."}]}]}
                """);
        var result = ResumeContentReviewRules.validate(output, sources, List.of(new ResumeContentReviewRules.Job(0,17,"Java API 개발")), mapper);
        assertThat(result.path("findings").get(0).path("quote").asText()).isEqualTo(quote);
        assertThat(result.path("jobs").get(0).path("criteria").get(0).path("resumeQuote").asText()).isEqualTo(quote);
        assertThat(result.toString()).doesNotContain("위조", "따옴표");
    }

    @Test void rejectsCrossSourceAndCrossJobIdsRatherThanTreatingThemAsMissingEvidence() throws Exception {
        var output = mapper.readTree("""
                {"findings":[],"jobs":[{"slot":0,"relevance":"RELATED","criteria":[
                {"jobQuoteId":"J1-P1","sourceId":"S1","resumeQuoteId":"S1-P1","assessment":"SUPPORTED","explanation":"연결"},
                {"jobQuoteId":"J0-P1","sourceId":"S1","resumeQuoteId":"S2-P1","assessment":"CHECK","explanation":"연결"},
                {"jobQuoteId":"J0-P1","sourceId":"","resumeQuoteId":"S1-P1","assessment":"CHECK","explanation":"연결"},
                {"jobQuoteId":"J0-P1","sourceId":"","resumeQuoteId":"","assessment":"CHECK","explanation":"확인 필요"}]}]}
                """);
        var result = ResumeContentReviewRules.validate(output, sources, List.of(new ResumeContentReviewRules.Job(0,17,"Java API 개발")), mapper);
        assertThat(result.path("jobs").get(0).path("criteria")).hasSize(1);
        assertThat(result.path("jobs").get(0).path("relevance").asText()).isEqualTo("REVIEW_NEEDED");
        assertThat(result.path("jobs").get(0).path("criteria").get(0).path("resumeQuote").asText()).isEmpty();
    }

    @Test void passagesAreStableBoundedSubstringsAndNeverBridgeRedactedText() {
        String text = "[익명] Java API를 개발했습니다.\n- 성능을 개선했습니다.\n" + "긴".repeat(1300) + "\n원문[익명]사이";
        var passages = ResumeContentReviewRules.passages("S1", text);
        assertThat(passages).isEqualTo(ResumeContentReviewRules.passages("S1", text));
        assertThat(passages).extracting(ResumeContentReviewRules.Passage::id).doesNotHaveDuplicates();
        assertThat(passages).allSatisfy(p -> {
            assertThat(p.text()).isNotBlank().hasSizeLessThanOrEqualTo(600).doesNotContain("[익명]", "원문사이");
            assertThat(text).contains(p.text());
        });
        assertThat(passages.getFirst().text()).isEqualTo(quote);
    }
    @Test void rejectsInventedQuotesNumbersAndSourcesAndDeduplicates() throws Exception {
        var response = mapper.readTree("""
            {"findings":[
             {"sourceId":"S1","quoteId":"S1-P1","issueType":"ROLE","problem":"담당 범위를 설명하세요.","actions":["직접 구현한 기능을 적으세요."],"rewriteTemplate":"[담당 기능]을 구현했습니다."},
             {"sourceId":"S1","quoteId":"S1-P1","issueType":"ROLE","problem":"담당 범위를 설명하세요.","actions":["직접 구현한 기능을 적으세요."],"rewriteTemplate":"[담당 기능]을 구현했습니다."},
             {"sourceId":"S1","quoteId":"S1-P1","issueType":"EVIDENCE","problem":"성과 설명","actions":["측정하세요"],"rewriteTemplate":"응답 시간을 180ms로 개선했습니다."},
             {"sourceId":"S1","quoteId":"S1-P999","issueType":"ROLE","problem":"설명","actions":["수정"],"rewriteTemplate":"[역할]"},
             {"sourceId":"FAKE","quoteId":"S1-P1","issueType":"ROLE","problem":"설명","actions":["수정"],"rewriteTemplate":"[역할]"}],"jobs":[]}
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
              {"jobQuoteId":"J0-P1","sourceId":"S1","resumeQuoteId":"S1-P1","assessment":"SUPPORTED","explanation":"API 개발 경험을 연결해 볼 수 있습니다."},
              {"jobQuoteId":"J0-P999","sourceId":"S1","resumeQuoteId":"S1-P1","assessment":"SUPPORTED","explanation":"일치"}]},
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
        var properties = payload.path("text").path("format").path("schema").path("properties");
        assertThat(properties.path("findings").path("items").path("properties").has("quoteId")).isTrue();
        assertThat(properties.path("findings").path("items").path("properties").has("quote")).isFalse();
        assertThat(payload.path("instructions").asText()).contains("UNTRUSTED DATA", "Never invent");
    }
}
