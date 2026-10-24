package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiJobFitTest {
    @Test void onlyGroundedCurrentDecisionsCanBeReused() {
        var mapper = new ObjectMapper();
        String hash = AiJobFit.jobFingerprint(Map.of("title", "Senior Software Engineer", "qualifications", "Own production architecture"));
        var review = mapper.createObjectNode().put("fitVersion", AiJobFit.VERSION).put("inputFingerprint", "resume-v1")
                .put("jobFingerprint", hash).put("decision", "NOT_RECOMMENDED");
        assertThat(AiJobFit.decision(review, "resume-v1", hash)).isEqualTo("NOT_RECOMMENDED");
        assertThat(AiJobFit.visible(AiJobFit.decision(review, "resume-v1", hash))).isFalse();
        assertThat(AiJobFit.decision(review, "resume-v2", hash)).isEqualTo("REVIEW_REQUIRED");
        assertThat(AiJobFit.decision(review, "resume-v1", AiJobFit.jobFingerprint(Map.of("title", "Junior Engineer"))))
                .isEqualTo("REVIEW_REQUIRED");
        review.remove("fitVersion");
        assertThat(AiJobFit.decision(review, "resume-v1", hash)).isEqualTo("REVIEW_REQUIRED");
        assertThat(AiJobFit.visible("RECOMMENDED")).isTrue();
        assertThat(AiJobFit.visible("STRETCH")).isTrue();
        assertThat(AiJobFit.visible("INSUFFICIENT_INFORMATION")).isFalse();
        assertThat(AiJobFit.visible("REVIEW_REQUIRED")).isFalse();
    }
}
