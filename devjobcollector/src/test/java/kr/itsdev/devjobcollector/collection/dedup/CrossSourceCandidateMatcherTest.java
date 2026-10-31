package kr.itsdev.devjobcollector.collection.dedup;

import org.junit.jupiter.api.Test;

import static kr.itsdev.devjobcollector.collection.dedup.CrossSourceCandidateMatcher.Decision.*;
import static org.assertj.core.api.Assertions.assertThat;

class CrossSourceCandidateMatcherTest {
    private final CrossSourceCandidateMatcher matcher = new CrossSourceCandidateMatcher();
    private CrossSourceCandidateMatcher.Posting job(Long company, String source, String title) {
        return new CrossSourceCandidateMatcher.Posting(company, source, "id", title);
    }

    @Test void verifiedSameCompanyAndMatchingTitleOnlyCreatesReviewCandidate() {
        assertThat(matcher.compare(job(7L, "WORK24", " Java  개발자 "), job(7L, "SARAMIN", "java 개발자")))
                .isEqualTo(REVIEW_EXACT_TITLE);
    }

    @Test void differingOrMissingTitlesStillNeedHumanReview() {
        assertThat(matcher.compare(job(7L, "WORK24", "백엔드 개발자"), job(7L, "SARAMIN", "서버 개발자")))
                .isEqualTo(REVIEW_SAME_COMPANY);
        assertThat(matcher.compare(job(7L, "WORK24", null), job(7L, "SARAMIN", null)))
                .isEqualTo(REVIEW_SAME_COMPANY);
    }

    @Test void neverComparesAcrossUnverifiedOrDifferentCompanies() {
        assertThat(matcher.compare(job(null, "WORK24", "개발자"), job(null, "SARAMIN", "개발자")))
                .isEqualTo(UNRESOLVED_COMPANY);
        assertThat(matcher.compare(job(1L, "WORK24", "개발자"), job(2L, "SARAMIN", "개발자")))
                .isEqualTo(DIFFERENT_COMPANY);
        assertThat(matcher.compare(job(1L, "WORK24", "개발자"), job(1L, "work24", "개발자")))
                .isEqualTo(SAME_SOURCE);
        assertThat(matcher.compare(job(0L, "WORK24", "개발자"), job(1L, "SARAMIN", "개발자")))
                .isEqualTo(INVALID_INPUT);
    }
}
