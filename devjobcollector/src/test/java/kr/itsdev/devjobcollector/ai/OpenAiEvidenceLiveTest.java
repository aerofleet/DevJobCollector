package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in smoke tests. Synthetic data only; one billable API request per test. */
@EnabledIfEnvironmentVariable(named="DJC_OPENAI_LIVE_TEST",matches="true")
class OpenAiEvidenceLiveTest {
    @Test void modelJudgesSeniorityFromEvidenceRatherThanFixedTitleYears() {
        var mapper = new ObjectMapper();
        var client = new OpenAiEvidenceClient(mapper, System.getenv("OPENAI_API_KEY"), true);
        String resume = "개발자로 1년 근무했습니다. Java API 개발 업무를 담당했습니다. 대규모 운영 아키텍처 설계나 팀 리딩을 담당한 경험은 없습니다.";
        var sources = java.util.List.of(new ResumeContentReviewRules.Source("S1", resume,
                new ResumeEvidenceReview.Document("/experience/0/description", "synthetic", null, "경력 1", resume)));
        var jobs = java.util.List.of(
                new ResumeContentReviewRules.Job(0, 1, "Senior Software Engineer, Cloudflare for SaaS. 필수: 개발 실무 경력 6년 이상. 대규모 운영 아키텍처 설계와 팀 기술 리딩 경험 필수."),
                new ResumeContentReviewRules.Job(1, 2, "Junior Backend Engineer. Java API 개발. 경력 0~2년 지원 가능."),
                new ResumeContentReviewRules.Job(2, 3, "Senior Software Engineer. 자격 요건과 담당 업무는 미수집."));
        var payload = java.util.Map.<String, Object>of("sources", java.util.List.of(java.util.Map.of(
                "sourceId", "S1", "section", "경력 1", "text", resume, "passages", ResumeContentReviewRules.passages("S1", resume))),
                "jobs", jobs.stream().map(j -> java.util.Map.of("slot", j.slot(), "text", j.text(), "passages", ResumeContentReviewRules.passages("J" + j.slot(), j.text()))).toList());
        var output = client.review(payload);
        var validated = ResumeContentReviewRules.validate(output, sources, jobs, mapper);
        assertThat(validated.path("jobs")).hasSize(3);
        var decisions = new java.util.HashMap<Long,String>();
        validated.path("jobs").forEach(j -> decisions.put(j.path("jobId").asLong(), j.path("decision").asText()));
        assertThat(decisions.get(1L)).as("Synthetic fit: %s", validated.path("jobs")).isEqualTo("NOT_RECOMMENDED");
        assertThat(decisions.get(2L)).isIn("RECOMMENDED", "STRETCH");
        assertThat(decisions.get(3L)).isEqualTo("INSUFFICIENT_INFORMATION");
    }
    @org.junit.jupiter.api.RepeatedTest(3)
    void contentModelReviewsSyntheticResumeWithVerifiableQuotesAndJobRequirements() {
        var mapper = new ObjectMapper();
        var client = new OpenAiEvidenceClient(mapper, System.getenv("OPENAI_API_KEY"), true);
        String text = "팀 프로젝트에서 Java와 Spring Boot를 사용했습니다. 성능을 개선했습니다. 프로젝트에 참여했습니다.";
        String requirements = "Java와 Spring Boot 기반 API 개발 경험. 본인이 수행한 API 설계와 테스트 경험을 설명할 수 있는 분.";
        var document = new ResumeEvidenceReview.Document("/projects/0/description", "synthetic", null, "프로젝트 1", text);
        var sources = java.util.List.of(new ResumeContentReviewRules.Source("S1", text, document));
        var jobs = java.util.List.of(new ResumeContentReviewRules.Job(0, 1, requirements));
        var payload = java.util.Map.<String, Object>of("sources", java.util.List.of(java.util.Map.of("sourceId", "S1", "kind", "SAVED_RESUME", "section", "프로젝트 1", "field", "description", "text", text,
                        "passages", ResumeContentReviewRules.passages("S1", text))),
                "jobs", java.util.List.of(java.util.Map.of("slot", 0, "text", requirements, "passages", ResumeContentReviewRules.passages("J0", requirements))));
        var output = client.review(payload);
        var validated = ResumeContentReviewRules.validate(output, sources, jobs, mapper);
        // Reject invented applicant facts even if a different valid item survives filtering.
        assertThat(output.path("positioning").asText()).doesNotContain("API", "설계", "테스트", "주도");
        for (var strength : output.path("strengths"))
            assertThat(strength.path("point").asText()).doesNotContain("API", "설계", "테스트", "주도");
        for (var rewrite : output.path("rewrites"))
            assertThat(rewrite.path("suggestion").asText()).doesNotContain("API", "설계", "테스트", "주도");
        assertThat(output.has("rewrites")).isTrue();
        assertThat(validated.path("positioning").asText()).as("Synthetic output: %s; validated: %s", output, validated).isNotBlank();
        assertThat(validated.path("strengths").size()).as("Synthetic output: %s; validated: %s", output, validated).isGreaterThan(0);
        for (var finding : validated.path("findings")) assertThat(finding.path("suggestion").asText()).isNotBlank().doesNotContain("[", "]");
        assertThat(validated.path("findings").size()).as("Synthetic output: %s; validated: %s", output, validated).isGreaterThan(0);
        assertThat(validated.path("jobs").size()).as("Synthetic output: %s; validated: %s", output, validated).isEqualTo(1);
        for (var finding : validated.path("findings")) assertThat(text).contains(finding.path("quote").asText());
        for (var criterion : validated.path("jobs").get(0).path("criteria")) {
            assertThat(requirements).contains(criterion.path("jobQuote").asText());
            // Both supplied job passages require activities absent from this sparse resume.
            assertThat(criterion.path("assessment").asText()).isEqualTo("CHECK");
            if (!criterion.path("resumeQuote").asText().isEmpty()) assertThat(text).contains(criterion.path("resumeQuote").asText());
        }
    }
    @Test void nanoReturnsSupportedEvidenceForSyntheticJobs() throws Exception {
        var mapper=new ObjectMapper();
        var client=new OpenAiEvidenceClient(mapper,System.getenv("OPENAI_API_KEY"),true);
        assertThat(client.available()).isTrue();
        var analysis=mapper.readTree("""
                {"recommendations":[
                  {"jobId":1,"components":{"skillExactMatch":30,"careerFit":15},"missingPoints":["synthetic"]},
                  {"jobId":2,"components":{"jobCategory":10},"unknownFactors":["careerFit"]}
                ]}
                """);
        var evidence=RecommendationEvidence.project(analysis);
        var output=client.select(evidence);
        assertThat(RecommendationEvidence.validateAndRender(output,evidence,analysis)).hasSize(2);
    }
}
