package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AiJobFitTest {
    @ParameterizedTest
    @ValueSource(strings = {"Senior Java Engineer", "SENIOR Backend", "senior engineer", "Sr. Engineer",
            "Sr Engineer", "Staff Engineer", "Principal Engineer", "Tech Lead", "시니어 개발자", "수석개발자",
            "Vice President of Engineering", "vice-president", "VICE PRESIDENT", "VP Engineering",
            "Director of Engineering", "director", "Engineering Manager", "Product Manager", "MANAGER"})
    void seniorRolesRequireReviewEvenWhenExperienceSaysUnrestricted(String title) {
        assertThat(AiJobFit.requiresSeniorityReview(Map.of("title", title, "experience", "경력무관"))).isTrue();
    }

    @Test void checksExperienceAndCategoryWithoutTreatingVendorsOrIncidentalBodyMentionsAsSeniority() {
        assertThat(AiJobFit.requiresSeniorityReview(Map.of("experience", "Senior"))).isTrue();
        assertThat(AiJobFit.requiresSeniorityReview(Map.of("job_category", "시니어 백엔드"))).isTrue();
        assertThat(AiJobFit.requiresSeniorityReview(Map.of("title", "Java Engineer", "company_name", "Cloudflare",
                "qualifications", "Work with senior engineers"))).isFalse();
        assertThat(AiJobFit.requiresSeniorityReview(Map.of("title", "Leadership tooling engineer"))).isFalse();
        assertThat(AiJobFit.requiresSeniorityReview(Map.of("title", "Directory services developer"))).isFalse();
        assertThat(AiJobFit.requiresSeniorityReview(Map.of("title", "Java Engineer", "qualifications", "Reports to a Director and Manager"))).isFalse();
        assertThat(AiJobFit.requiresSeniorityReview(Map.of())).isFalse();
    }

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
        review.put("fitVersion", "ai-job-fit-v2");
        assertThat(AiJobFit.decision(review, "resume-v1", hash)).isEqualTo("REVIEW_REQUIRED");
        review.remove("fitVersion");
        assertThat(AiJobFit.decision(review, "resume-v1", hash)).isEqualTo("REVIEW_REQUIRED");
        assertThat(AiJobFit.visible("RECOMMENDED")).isTrue();
        assertThat(AiJobFit.visible("STRETCH")).isTrue();
        assertThat(AiJobFit.visible("INSUFFICIENT_INFORMATION")).isFalse();
        assertThat(AiJobFit.visible("REVIEW_REQUIRED")).isFalse();
    }
}
