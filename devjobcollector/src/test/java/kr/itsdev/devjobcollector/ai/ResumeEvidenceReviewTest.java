package kr.itsdev.devjobcollector.ai;

import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResumeEvidenceReviewTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "서비스 성능을 개선하는 개발자가 되고 싶습니다.", "응답 속도를 개선하겠습니다.",
            "처리 속도를 개선할 계획입니다.", "I want to improve API latency.",
            "ms 단위의 최적화를 고민하는 개발자", "성능 개선과 최적화", "API 성능 개선" })
    void aspirationsAndHeadingsAreNotMissingMeasurementFindings(String text) {
        var review = review(text);
        assertThat(review.path("findings")).isEmpty();
        assertThat(review.path("observations").path("outcomeSentences").asInt()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "성능 개선을 위해 active=10, pending=10, max=10으로 풀 포화를 진단했습니다.",
            "p95=9.9로 응답 시간을 단축했습니다.", "응답 시간을 67s→9.9s로 단축했습니다.",
            "오류율을 65%→0%로 감소시켰습니다.", "API 응답 시간을 20ms로 단축했습니다.",
            "처리량을 120RPS로 개선했습니다." })
    void recognizesMetricAssignmentsAndTimeUnits(String text) {
        var review = review(text);
        assertThat(review.path("findings")).isEmpty();
        assertThat(review.path("observations").path("outcomeSentences").asInt()).isEqualTo(1);
        assertThat(review.path("observations").path("withMeasurement").asInt()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "API 성능을 개선했습니다.", "2025년 API 성능을 개선했습니다.",
            "p95 지표를 개선했습니다.", "ms 단위로 응답 시간을 단축했습니다.",
            "issue_id=42의 성능을 개선했습니다." })
    void retainsActualUnmeasuredClaimsWithoutCountingYearMetricNamesOrIdentifiers(String text) {
        var review = review(text);
        assertThat(review.path("findings")).hasSize(1);
        assertThat(review.path("findings").get(0).path("quote").asText()).isEqualTo(text);
        assertThat(review.path("observations").path("withMeasurement").asInt()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"\n- ", "\r\n• ", " • ", " - ", "\n1. "})
    void countsBulletOutcomesWithoutSplittingDecimals(String separator) {
        String text = String.join(separator, "오류율 65%→0%", "p95 67s→9.9s", "처리량 10RPS→20RPS", "응답 시간을 20ms로 단축했습니다.");
        var review = review(text);
        assertThat(review.path("observations").path("outcomeSentences").asInt()).isEqualTo(4);
        assertThat(review.path("observations").path("withMeasurement").asInt()).isEqualTo(4);
        assertThat(review.path("findings")).isEmpty();
        assertThat(ResumeEvidenceReview.sentences(text)).hasSize(4).allSatisfy(quote -> assertThat(text).contains(quote));
    }

    private com.fasterxml.jackson.databind.JsonNode review(String text) {
        return new ObjectMapper().valueToTree(ResumeEvidenceReview.review(List.of(
                new ResumeEvidenceReview.Document("/careerProfile/summary", "저장된 이력서", null, "careerProfile", text)), Set.of()));
    }

    @Test void rejectsInventedQuotesAndUntrustedSourcesAndMergesExactDuplicates() {
        var a = new ResumeEvidenceReview.Document("a", "resume.pdf", 2, "첨부파일", "API 응답 속도를 개선했습니다.");
        var b = new ResumeEvidenceReview.Document("b", "저장된 이력서", null, "projects", a.text());
        var fake = new ResumeEvidenceReview.Document("fake", "fake.pdf", 99, "첨부파일", "위조 문장");
        var findings = ResumeEvidenceReview.validate(List.of(
                proposal(a, a.text()), proposal(b, b.text()), proposal(a, "300ms에서 180ms로 개선"), proposal(fake, fake.text())), List.of(a, b));
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().locations()).hasSize(2);
        assertThat(findings.getFirst().locations().getFirst().page()).isEqualTo(2);
        assertThat(findings.getFirst().quote()).isEqualTo(a.text());
    }
    @Test void reportsObservationsNotAnInventedScoreAndDoesNotTreatYearsAsMeasurement() {
        var doc = new ResumeEvidenceReview.Document("a", "resume.pdf", 1, "첨부파일", "2025년 API 성능을 개선했습니다. 응답 시간을 20ms로 단축했습니다.");
        var review = new ObjectMapper().valueToTree(ResumeEvidenceReview.review(List.of(doc), Set.of()));
        assertThat(review.path("score").path("status").asText()).isEqualTo("NOT_SCORED");
        assertThat(review.path("score").has("total")).isFalse();
        assertThat(review.path("observations").path("outcomeSentences").asInt()).isEqualTo(2);
        assertThat(review.path("observations").path("withMeasurement").asInt()).isEqualTo(1);
        assertThat(review.path("findings").size()).isEqualTo(1);
        assertThat(review.path("findings").get(0).path("rewriteTemplate").asText()).contains("[측정 전 값·단위]");
    }
    @Test void skillLocationsUseBoundariesAndAliases() {
        var docs = List.of(new ResumeEvidenceReview.Document("a", "resume.pdf", 3, "첨부파일", "JavaScript, Spring Boot"));
        var review = new ObjectMapper().valueToTree(ResumeEvidenceReview.review(docs, Set.of("java", "springboot")));
        assertThat(review.path("skillEvidence").has("java")).isFalse();
        assertThat(review.path("skillEvidence").path("springboot").get(0).path("page").asInt()).isEqualTo(3);
    }
    private ResumeEvidenceReview.Proposal proposal(ResumeEvidenceReview.Document doc, String quote) {
        return new ResumeEvidenceReview.Proposal("metric", quote, "problem", "[측정값]", doc);
    }
}
