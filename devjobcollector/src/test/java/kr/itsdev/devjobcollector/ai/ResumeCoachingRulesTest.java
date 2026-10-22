package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResumeCoachingRulesTest {
    final ObjectMapper mapper = new ObjectMapper();
    final String source = "6인 팀에서 API를 개발했습니다. 실패율을 65%에서 0%로 낮췄습니다.";
    final List<ResumeContentReviewRules.Source> sources = List.of(new ResumeContentReviewRules.Source("S1",source,
            new ResumeEvidenceReview.Document("/projects/0/description","이력서",null,"projects",source)));
    ObjectNode output() throws Exception {
        return (ObjectNode) mapper.readTree("""
          {"positioning":"실패율을 낮추는 개발자","positioningEvidence":[{"sourceId":"S1","quoteId":"S1-P2"}],
           "strengths":[{"point":"실패율 개선","evidence":[{"sourceId":"S1","quoteId":"S1-P2"}],"placement":"프로젝트 첫 줄에 두세요"}],
           "gaps":[],"rewrites":[{"sourceId":"S1","quoteId":"S1-P1","evidence":[{"sourceId":"S1","quoteId":"S1-P2"}],
           "suggestion":"6인 팀에서 API를 개발하고 실패율을 65%에서 0%로 낮췄습니다.","reason":"역할과 결과를 함께 보여주세요.",
           "confirm":[{"question":"API를 단독으로 담당했나요?","options":[
           {"label":"단독 담당","suggestion":"6인 팀에서 API를 단독 개발했습니다."},
           {"label":"공동 담당","suggestion":"6인 팀에서 API를 공동 개발했습니다."}]}]}],"job_fit":[]}
          """);
    }
    @Test void restoresEvidenceAndChangesOnlyConfirmedDraftWithUndo() throws Exception {
        var result = ResumeContentReviewRules.validate(output(),sources,List.of(),mapper);
        assertThat(result.path("strengths").size()).isEqualTo(1);
        assertThat(result.path("positioning").asText()).isNotBlank();
        var f = result.path("findings").get(0); String id = f.path("id").asText();
        assertThat(f.path("suggestion").asText()).doesNotContain("단독");
        ResumeCoachingRules.answer(result,id,0);
        assertThat(f.path("suggestion").asText()).contains("단독");
        ResumeCoachingRules.answer(result,id,1);
        assertThat(f.path("suggestion").asText()).contains("공동").doesNotContain("단독");
        ResumeCoachingRules.answer(result,id,-1);
        assertThat(f.path("suggestion").asText()).contains("65%").doesNotContain("단독","공동");
        assertThatThrownBy(() -> ResumeCoachingRules.answer(result,id,3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResumeCoachingRules.answer(result,"other",0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsPlaceholdersInventedNumbersAndForgedEvidence() throws Exception {
        for (String suggestion : List.of("[역할]을 담당했습니다.","실패율을 99% 낮췄습니다.","API 설계와 테스트를 주도했습니다.","Kubernetes로 API를 운영했습니다.")) {
            var out=output(); ((ObjectNode)out.path("rewrites").get(0)).put("suggestion",suggestion);
            assertThat(ResumeContentReviewRules.validate(out,sources,List.of(),mapper).path("findings").size()).isZero();
        }
        var out=output(); ((ObjectNode)out.path("rewrites").get(0)).put("quoteId","S1-P999");
        ((ObjectNode)out.path("strengths").get(0).path("evidence").get(0)).put("sourceId","FORGED");
        var result=ResumeContentReviewRules.validate(out,sources,List.of(),mapper);
        assertThat(result.path("findings").size()).isZero(); assertThat(result.path("strengths").size()).isZero();
    }
    @Test void filtersNoiseAliasesAndNegativeContextsWithoutRemovingPositiveEvidence() {
        var vocabulary = List.of("테스트","환경","Prometheus/Grafana","Grafana","Kafka","RabbitMQ","Kotlin","MongoDB","AWS","Java");
        var found = SavedResumeAnalysisInput.findSkills("테스트 환경. Prometheus/Grafana 사용. Kafka 대신 RabbitMQ 사용. Kotlin은 미사용. MongoDB는 비교만 했습니다. AWS를 사용하지 않았습니다. Java로 구현했습니다.",vocabulary);
        assertThat(found).containsExactlyInAnyOrder("prometheus","grafana","rabbitmq","java");
        assertThat(SavedResumeAnalysisInput.findSkills("Kafka는 미사용. 이후 Kafka를 운영했습니다.",vocabulary)).contains("kafka");
        assertThat(SavedResumeAnalysisInput.findSkills("Kafka와 RabbitMQ를 비교했습니다. Kotlin vs Java",vocabulary)).isEmpty();
        assertThat(ResumeSkillVocabulary.split("CI/CD")).containsExactly("CI/CD");
    }
}
