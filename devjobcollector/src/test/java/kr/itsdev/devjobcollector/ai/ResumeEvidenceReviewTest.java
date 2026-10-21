package kr.itsdev.devjobcollector.ai;

import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResumeEvidenceReviewTest {
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
