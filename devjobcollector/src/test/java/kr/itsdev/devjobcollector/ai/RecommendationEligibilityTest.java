package kr.itsdev.devjobcollector.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecommendationEligibilityTest {
    private LocalRecommendationEngine.Candidate job(String title, String experience) {
        return new LocalRecommendationEngine.Candidate(1, "백엔드", experience, "서울", "정규직",
                LocalDate.now(), List.of("Java", "Cloudflare"), false, title);
    }
    @Test void juniorCannotBypassSeniorityWithPerfectSkillsOrUnspecifiedExperience() {
        var engine = new LocalRecommendationEngine(30,15,10,10,5,5);
        for (String title : List.of("Senior Software Engineer, Cloudflare for SaaS", "시니어 백엔드 개발자",
                "Sr. Java Engineer", "Staff Engineer", "Principal Engineer", "Lead Backend Engineer", "Engineering Manager")) {
            for (Integer years : new Integer[]{0, 1, 3, null}) {
                var preferences = new LocalRecommendationEngine.Preferences(years, "백엔드", "서울", "정규직");
                assertThat(engine.rank(Set.of("java", "cloudflare"), preferences, List.of(job(title, "경력무관")), LocalDate.now()))
                        .as("%s / career=%s", title, years).isEmpty();
            }
        }
    }
    @Test void explicitYearsAndPolicyBoundariesAreDistinguished() {
        assertThat(RecommendationEligibility.assess(job("Senior Engineer", "경력무관"), 5).eligible()).isTrue();
        assertThat(RecommendationEligibility.assess(job("Staff Engineer", ""), 6).eligible()).isFalse();
        assertThat(RecommendationEligibility.assess(job("Staff Engineer", ""), 7).eligible()).isTrue();
        assertThat(RecommendationEligibility.assess(job("Principal Engineer", ""), 8).eligible()).isTrue();
        assertThat(RecommendationEligibility.assess(job("Senior Engineer (3+ years)", ""), 3).eligible()).isTrue();
        assertThat(RecommendationEligibility.assess(job("Senior Engineer (3+ years)", "7년 이상"), 3).eligible()).isFalse();
        for (String requirement : List.of("3년 이상", "3~5년", "3-5 years", "3+ years", "at least 3 years")) {
            assertThat(RecommendationEligibility.assess(job("Backend Engineer", requirement), 2).eligible()).as(requirement).isFalse();
        }
    }
    @Test void unknownCareerIsNotZeroAndMixedLevelsRemainAvailable() {
        assertThat(RecommendationEligibility.assess(job("Backend Engineer", "3년 이상"), null).reason()).isEqualTo("CAREER_UNCONFIRMED");
        assertThat(RecommendationEligibility.assess(job("Senior Engineer", ""), null).reason()).isEqualTo("CAREER_REQUIRED_FOR_SENIOR");
        for (String title : List.of("Junior / Senior Backend Engineer", "주니어·시니어 개발자", "Java 17 Developer", "Spring Engineer", "Backend Engineer - Sales Platform")) {
            assertThat(RecommendationEligibility.assess(job(title, "경력무관"), 0).eligible()).as(title).isTrue();
        }
        assertThat(RecommendationEligibility.minimumYears("3년 이하")).isNull();
    }
    @Test void vendorSkillsDoNotQualifyCommercialPositions() {
        for (String title : List.of("Senior Cloudflare One GTM Specialist", "Account Executive", "Cloudflare Sales Engineer")) {
            assertThat(RecommendationEligibility.assess(job(title, "경력무관"), 10).reason()).isEqualTo("NON_DEVELOPMENT_ROLE");
        }
    }
}
