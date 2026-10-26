package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecommendationLocationsTest {
    @Test void acceptsOverseasAndLegacySelections() throws Exception {
        var input = new ObjectMapper().readTree("[\"서울\",\"해외 전체\",\"미국\",\"일본\",\"원격\",\"invalid\",\"미국\"]");
        assertThat(SavedResumeAnalysisInput.allowed(input, RecommendationLocations.OPTIONS))
                .containsExactly("서울", "해외 전체", "일본", "미국", "원격");
    }
    @Test void matchesCountriesAndRegionsAcrossLanguagesWithoutSubstringFalsePositives() {
        assertThat(RecommendationLocations.matches("Tokyo, Japan", List.of("일본"))).isTrue();
        assertThat(RecommendationLocations.matches("San Francisco, United States", List.of("미국"))).isTrue();
        assertThat(RecommendationLocations.matches("Singapore", List.of("해외 전체"))).isTrue();
        assertThat(RecommendationLocations.matches("Seoul, South Korea", List.of("국내 전체"))).isTrue();
        assertThat(RecommendationLocations.matches("Seoul", List.of("일본", "서울"))).isTrue();
        assertThat(RecommendationLocations.matches("Remote", List.of("원격"))).isTrue();
        assertThat(RecommendationLocations.matches("Seoul", List.of("해외 전체"))).isFalse();
        assertThat(RecommendationLocations.matches("Busan, Australia", List.of("미국"))).isFalse();
        assertThat(RecommendationLocations.matches("Indonesia", List.of("인도"))).isFalse();
        assertThat(RecommendationLocations.matches("미정", List.of("해외 전체"))).isFalse();
        assertThat(RecommendationLocations.matches(null, List.of("일본"))).isFalse();
        assertThat(RecommendationLocations.matches("Japan", List.of())).isFalse();
    }
    @Test void selectedOverseasLocationExcludesOtherwiseIdenticalDomesticJob() {
        var today = java.time.LocalDate.now();
        var engine = new LocalRecommendationEngine(30, 15, 10, 10, 5, 5);
        var preferences = new LocalRecommendationEngine.Preferences(1, "백엔드", "일본", "정규직");
        var jobs = List.of(
                new LocalRecommendationEngine.Candidate(1, "백엔드", "경력무관", "Seoul", "정규직", today, List.of("Java")),
                new LocalRecommendationEngine.Candidate(2, "백엔드", "경력무관", "Tokyo, Japan", "정규직", today, List.of("Java")));
        var ranked = engine.rank(java.util.Set.of("java"), preferences, jobs, today);
        assertThat(ranked.getFirst().jobId()).isEqualTo(2);
        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().components().get("locationFit")).isEqualTo(10);
    }
    @Test void seventeenDomesticSelectionsNeverAdmitForeignOrUnlocatedJobs() {
        var selected = RecommendationLocations.DOMESTIC_REGIONS;
        for (String location : List.of("Seoul", "대한민국", "South Korea", "전국", "부산"))
            assertThat(RecommendationLocations.eligible(location, selected)).as(location).isTrue();
        for (String location : List.of("United States", "Japan", "Remote, US", "Remote", "미정", ""))
            assertThat(RecommendationLocations.eligible(location, selected)).as(location).isFalse();
        assertThat(RecommendationLocations.eligible(null, selected)).isFalse();
        assertThat(RecommendationLocations.eligible("Japan", List.of())).isTrue();
        assertThat(RecommendationLocations.eligible("Japan", List.of("서울", "일본"))).isTrue();
    }
}
