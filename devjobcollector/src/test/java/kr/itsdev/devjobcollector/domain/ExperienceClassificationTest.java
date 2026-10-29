package kr.itsdev.devjobcollector.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExperienceClassificationTest {
    @Test void requiredYearsOverrideSyntheticUnrestrictedValue() {
        var actual = ExperienceClassification.classify("경력무관", "개발자", "자격요건\n- 개발 경력 7년 이상\n우대사항\n- 실무 경력 10년 이상", false);
        assertThat(actual.category()).isEqualTo("EXPERIENCED");
        assertThat(actual.label()).isEqualTo("경력 7년 이상");
    }

    @Test void preferredYearsAloneAreNotMandatory() {
        var actual = ExperienceClassification.classify("경력무관", "개발자", "우대사항\n- 개발 경력 5년 이상", false);
        assertThat(actual.category()).isEqualTo("UNKNOWN");
    }

    @Test void missingExperienceIsNotUnrestricted() {
        assertThat(ExperienceClassification.classify("경력무관", "개발자", "서비스 개발", false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify("경력무관", "개발자", "경력 무관", false).category()).isEqualTo("ANY");
        assertThat(ExperienceClassification.classify("경력무관", "개발자", "자격요건\n경력 3~5년", false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify("경력무관", "개발자", "팀 소개\n팀장의 경력 7년 이상", false).category()).isEqualTo("UNKNOWN");
    }

    @Test void structuredAndEnglishRequirementsAreRecognized() {
        assertThat(ExperienceClassification.classify("3년 이상", "개발자", "", false).category()).isEqualTo("EXPERIENCED");
        assertThat(ExperienceClassification.classify(null, "engineer", "Requirements\n5+ years experience", false).label()).isEqualTo("경력 5년 이상");
        assertThat(ExperienceClassification.classify("신입·경력", "개발자", "", false).category()).isEqualTo("BOTH");
        assertThat(ExperienceClassification.classify(null, "개발자", "자격요건\n실무 5년", false).label()).isEqualTo("경력 5년");
    }
}
