package kr.itsdev.devjobcollector.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExperienceClassificationTest {
    @Test void recognizesMinimumExperienceInLookingForSection() {
        String text = "What we’re looking for:\n• Minimum 5 years experience scoping and conducting product research, ideally in ad tech\nRelocation Statement:\n• Minimum 10 years experience";
        var result = ExperienceClassification.classify(null, "Researcher", text, false);
        assertThat(result.category()).isEqualTo("EXPERIENCED");
        assertThat(result.label()).isEqualTo("경력 5년 이상");
        assertThat(ExperienceClassification.classify(null, "Researcher", "What we're looking for:\n• Minimum 5 years experience preferred", false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify(null, "Researcher", "What we're looking for:\n• Communication\nRelocation Statement:\n• Minimum 10 years experience", false).category()).isEqualTo("UNKNOWN");
    }
    @org.junit.jupiter.api.Test void recognizesExplicitUnrestrictedCareerAndAlternativeDegree() {
        String text = "Required Skills ﻿· 경력구분 : 무관 (신입 및 경력) · ※ 석사/박사 학위 소지 또는 5년 이상의 연구/개발 경력 · Python 개발\nPreferred Skills ﻿· 10년 이상 경력";
        var actual = ExperienceClassification.classify(null, "Engineer", text, false);
        assertThat(actual.category()).isEqualTo("ANY");
        assertThat(actual.label()).isEqualTo("경력무관");
        assertThat(ExperienceClassification.classify(null, "Engineer", "Required Skills\n· 석사 학위 또는 5년 이상의 개발 경력", false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify(null, "Engineer", "Preferred Skills\n· 경력구분 : 무관", false).category()).isEqualTo("UNKNOWN");
    }
    @Test void separatesMandatoryYearsFromPreferredClause() {
        String text = "Responsibilities\n• Advise on benefits\nQualifications & Experience Required\n• 10+ years of relevant employment-law experience; in-house experience strongly preferred.\n• Experience with compensation and benefits\nPay Range Transparency\n20+ years of experience";
        var result = ExperienceClassification.classify("경력무관", "Counsel", text, false);
        assertThat(result.label()).isEqualTo("경력 10년 이상");
        assertThat(result.evidence()).contains("10+ years").doesNotContain("preferred");
        assertThat(ExperienceClassification.classify(null, "Counsel", "Qualifications & Experience Required\n• 10+ years of experience strongly preferred.", false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify(null, "Counsel", "Qualifications & Experience Required\n• Experience with benefits\n• 5+ years of legal experience", false).label()).isEqualTo("경력 5년 이상");
    }
    @Test void recognizesLookingForSomeoneWhoWithoutSuccessMeasureContamination() {
        String text = "What you’ll do\n- Manage engineers\nWe’re looking for someone who\n- Has at least 8 years of professional software development experience\n- Has at least 4 years of people management experience\nSuccess Measures\n- At least 20 years of experience would be unusual";
        var result = ExperienceClassification.classify("경력무관", "Engineering Manager", text, false);
        assertThat(result.label()).isEqualTo("경력 8년 이상");
        assertThat(result.evidence()).contains("8 years");
    }
    @Test void recognizesRequiredSkillsAndExperienceButNotPreferredYears() {
        String text = "What you'll do:\n- Support customers\nRequired Skills and Experience:\n- 2+ years of experience in customer support\nPreferred qualifications:\n- 7+ years of experience";
        var result = ExperienceClassification.classify("경력무관", "Support", text, false);
        assertThat(result.label()).isEqualTo("경력 2년 이상");
        assertThat(result.evidence()).contains("2+ years");
        assertThat(ExperienceClassification.classify(null, "Support", "Preferred qualifications:\n- 7+ years of experience", false).category()).isEqualTo("UNKNOWN");
    }
    @Test void recognizesExperienceInTossQualificationSectionButNotPreferredOrOtherSections() {
        String text = "# 이런 분과 함께하고 싶어요\n- 자금 오퍼레이션 업무를 2년 이상 경험하신 분이면 좋아요\n# 이런 경험이 있다면 더 좋아요\n- 자금 업무를 7년 이상 경험하신 분\n# 토스로의 합류 여정\n- 9년 이상 경험";
        var result = ExperienceClassification.classify("경력무관", "Treasury Manager", text, false);
        assertThat(result.category()).isEqualTo("EXPERIENCED");
        assertThat(result.label()).isEqualTo("경력 2년 이상");
        assertThat(result.evidence()).contains("2년 이상 경험");
        assertThat(ExperienceClassification.classify(null, "Role", "# 이런 경험이 있다면 더 좋아요\n- 7년 이상 경험", false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify(null, "Role", "# 이런 분과 함께하고 싶어요\n- 5년 이상 경험 우대", false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify(null, "Role", "# 이런 분과 함께하고 싶어요\n- 협업 능력\n# 이력서 작성 가이드\n- 9년 이상 경험", false).category()).isEqualTo("UNKNOWN");
    }
    @Test void classifiesExpertiseAndExcludesInlinePreferredExperience() {
        var actual = ExperienceClassification.classify("경력무관", "Accountant", "Your Expertise:\n4+ years of accounting experience, with at least 1 year in month-end close.\nPreferred: 8+ years of accounting experience", false);
        assertThat(actual.label()).isEqualTo("경력 4년 이상");
        assertThat(actual.evidence()).contains("4+ years");
        assertThat(ExperienceClassification.classify(null, "Accountant", "Your Expertise:\nPreferred: 8+ years of accounting experience", false).category()).isEqualTo("UNKNOWN");
    }
    @Test void recognizesFigmaRequiredExperienceButExcludesPreferred() {
        String required = "We'd love to hear from you if you have:\n3+ years of experience leading engineering teams\nWhile not required, it’s an added plus if you also have:\n8+ years of experience with design systems";
        var result = ExperienceClassification.classify("경력무관", "Manager", required, false);
        assertThat(result.label()).isEqualTo("경력 3년 이상");
        assertThat(result.evidence()).contains("3+ years");
        assertThat(ExperienceClassification.classify(null, "Manager", "While not required, it’s an added plus if you also have:\n8+ years of experience", false).category()).isEqualTo("UNKNOWN");
    }
    @Test void recognizesExplicitEnglishMinimumRangeOnlyInRequirements() {
        String line = "Minimum of 2-3 years of relevant experience in customer support.";
        var actual = ExperienceClassification.classify("경력무관", "Support", "What we look for in you (i.e. job requirements):\n" + line, false);
        assertThat(actual.category()).isEqualTo("EXPERIENCED");
        assertThat(actual.label()).isEqualTo("경력 최소 2~3년");
        assertThat(actual.evidence()).isEqualTo(line);
        assertThat(ExperienceClassification.classify(null, "Support", "Nice to haves:\n" + line, false).category()).isEqualTo("UNKNOWN");
        assertThat(ExperienceClassification.classify(null, "Support", "Requirements\n2-3 years of experience", false).category()).isEqualTo("UNKNOWN");
    }
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
        assertThat(ExperienceClassification.classify("경력 18개월 이상", "개발자", "자격요건\n경력 5년 이상", false).label()).isEqualTo("경력 18개월 이상");
        assertThat(ExperienceClassification.classify(null, "engineer", "Requirements\n5+ years experience", false).label()).isEqualTo("경력 5년 이상");
        assertThat(ExperienceClassification.classify("신입·경력", "개발자", "", false).category()).isEqualTo("BOTH");
        assertThat(ExperienceClassification.classify(null, "개발자", "자격요건\n실무 5년", false).label()).isEqualTo("경력 5년");
    }

    @Test void separatesInlineKakaoSectionsAndIgnoresPreferredExperience() {
        var result = ExperienceClassification.classify("경력무관", "Backend Engineer",
                "◆ 조직소개 - 시스템을 개발합니다. ◆ 업무내용 - 서버 운영 ◆ 지원자격 - 백엔드 개발 경력 3년 이상인 분 - 협업 경험 ◆ 우대사항 - 실무 경력 7년 이상인 분 ◆ 접수 방법 - 원문 확인", false);
        assertThat(result.category()).isEqualTo("EXPERIENCED");
        assertThat(result.label()).isEqualTo("경력 3년 이상");
    }

    @Test void extractsYearsBeforeLongRoleDescriptionFromEncodedHtml() {
        String html = "&lt;p&gt;&lt;strong&gt;자격&lt;/strong&gt; &lt;strong&gt;요건&lt;/strong&gt;&lt;/p&gt;"
                + "&lt;ul&gt;&lt;li&gt;&lt;strong&gt;3&lt;/strong&gt;&lt;strong&gt;년 이상&lt;/strong&gt;의 상용 환경 내 머신러닝, 자연어 처리(NLP) 또는 AI 모델 개발, 학습 및 배포 실무 경력을 보유하신 분&lt;/li&gt;&lt;/ul&gt;"
                + "&lt;p&gt;우대사항&lt;/p&gt;&lt;ul&gt;&lt;li&gt;실무 경력 7년 이상&lt;/li&gt;&lt;/ul&gt;";
        var result = ExperienceClassification.classify("경력무관", "ML Engineer", html, false);
        assertThat(result.label()).isEqualTo("경력 3년 이상");
        assertThat(result.method()).isEqualTo("RULE");
        assertThat(result.evidence()).contains("3년 이상", "실무 경력");
    }

    @Test void evaluatesHundredSyntheticMandatoryAndPreferredExamples() {
        String[] roles = {"백엔드", "프론트엔드", "데이터", "머신러닝", "클라우드", "보안", "플랫폼", "모바일", "시스템", "검색"};
        int checked = 0;
        for (String role : roles) {
            for (int years = 1; years <= 5; years++) {
                String line = years + "년 이상의 " + role + " 개발 및 운영 실무 경력을 보유하신 분";
                assertThat(ExperienceClassification.classify("경력무관", role, "자격요건\n" + line, false).label())
                        .isEqualTo("경력 " + years + "년 이상");
                assertThat(ExperienceClassification.classify("경력무관", role, "우대사항\n" + line, false).category())
                        .isEqualTo("UNKNOWN");
                checked += 2;
            }
        }
        assertThat(checked).isEqualTo(100);
    }
}
