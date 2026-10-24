package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocalRecommendationEngineTest {
    @Test void roleAliasesQualifyWithoutSkillTagsAndDoNotMatchUnrelatedRoles() {
        var engine = new LocalRecommendationEngine(30,15,10,10,5,5);
        var prefs = new LocalRecommendationEngine.Preferences(null, "백엔드", "", "");
        for (String title : List.of("백엔드 지원", "Backend Engineer", "Back-end Developer", "서버 개발자")) {
            assertThat(RecommendationRoles.detect(title)).contains("백엔드");
            var candidate = new LocalRecommendationEngine.Candidate(1, title, null, null, null, LocalDate.now(), List.of());
            assertThat(engine.rank(Set.of(), prefs, List.of(candidate), LocalDate.now())).hasSize(1);
        }
        assertThat(RecommendationRoles.detect("회계 지원 / 고객지원 / 프론트엔드")).doesNotContain("백엔드");
    }
    final LocalRecommendationEngine engine = new LocalRecommendationEngine(30,15,10,10,5,5);
    final LocalDate today = LocalDate.of(2026,10,20);
    final LocalRecommendationEngine.Preferences preferences =
            new LocalRecommendationEngine.Preferences(3,"백엔드","서울","정규직");

    @Test void matchesAnySelectedConditionWithoutMultiplyingItsWeight() {
        var multi = new LocalRecommendationEngine.Preferences(3, List.of("백엔드", "프론트엔드"),
                List.of("서울", "경기"), List.of("정규직", "인턴"));
        var second = new LocalRecommendationEngine.Candidate(2, "프론트엔드", "3년 이상", "경기", "인턴", today, List.of("Java"));
        var both = new LocalRecommendationEngine.Candidate(3, "백엔드 프론트엔드", "3년 이상", "서울 경기", "정규직 인턴", today, List.of("Java"));
        var result = engine.rank(Set.of("java"), multi, List.of(job(1, "Java"), second, both), today);
        assertThat(result).hasSize(3).allSatisfy(item -> assertThat(item.matchScore()).isEqualTo(100));
        assertThat(engine.rank(Set.of(), multi, List.of(second), today)).hasSize(1);
        assertThat(engine.rank(Set.of(), new LocalRecommendationEngine.Preferences(null, List.of(), List.of(), List.of()), List.of(second), today)).isEmpty();
    }

    @Test void ranksExactFitAndKeepsDeterministicTies() {
        var result = engine.rank(Set.of("java"), preferences, List.of(job(2,"Java"), job(1,"Java"), job(3,"Python")), today);
        assertThat(result).extracting(LocalRecommendationEngine.Recommendation::jobId).containsExactly(1L,2L,3L);
        assertThat(result.getFirst().matchScore()).isEqualTo(100);
        assertThat(result.get(2).missingPoints()).containsExactly("python");
        assertThat(result.getFirst().unknownFactors()).contains("semanticSimilarity");
    }
    @Test void excludesUnrelatedJobsAndLimitsToFive() {
        var unrelated = new LocalRecommendationEngine.Candidate(99,"디자인",null,null,null,today,List.of("Figma"));
        assertThat(engine.rank(Set.of("java"), preferences,List.of(unrelated),today)).isEmpty();
        var jobs = java.util.stream.LongStream.range(1,10).mapToObj(id -> job(id,"Java")).toList();
        assertThat(engine.rank(Set.of("java"),preferences,jobs,today)).hasSize(5);
    }
    @Test void careerBoundsAndUnknownsAreExplicit() {
        assertThat(LocalRecommendationEngine.careerFit("3~5년",3)).isEqualTo(1);
        assertThat(LocalRecommendationEngine.careerFit("3~5년",6)).isZero();
        assertThat(LocalRecommendationEngine.careerFit("3년 이상",2)).isZero();
        assertThat(LocalRecommendationEngine.careerFit("3년 미만",3)).isZero();
        assertThat(LocalRecommendationEngine.careerFit("경력",3)).isNull();
        assertThat(LocalRecommendationEngine.careerFit("신입",0)).isEqualTo(1);
    }
    @Test void normalizesSkillsAndNeverCopiesPersonalTextIntoReview() throws Exception {
        var content = new ObjectMapper().readTree("""
                {"techStack":[{"name":"JS"},{"name":"JavaScript"}],
                 "projects":[{"description":"홍길동 private@example.com 프로젝트"}]}
                """);
        assertThat(engine.skills(content)).containsExactly("javascript");
        assertThat(engine.review(content)).hasSize(1);
        assertThat(engine.review(content).toString()).doesNotContain("홍길동","private@example.com");
        assertThat(engine.review(content).getFirst().sourcePath()).isEqualTo("/projects/0/description");
    }
    private LocalRecommendationEngine.Candidate job(long id,String skill) {
        return new LocalRecommendationEngine.Candidate(id,"백엔드","3년 이상","서울","정규직",today,List.of(skill));
    }
}
