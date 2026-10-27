package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JobComparisonDiagnosticsTest {
    final ObjectMapper mapper = new ObjectMapper();
    final String text = "Java API를 개발했습니다.";
    final List<ResumeContentReviewRules.Source> sources = List.of(new ResumeContentReviewRules.Source("S1", text,
            new ResumeEvidenceReview.Document("/projects/0/description", "synthetic", null, "projects", text)));
    final List<ResumeContentReviewRules.Job> jobs = List.of(new ResumeContentReviewRules.Job(0, 17, "Java API 개발"));

    ObjectNode output() {
        var output = mapper.createObjectNode();
        for (String field : List.of("strengths", "gaps", "rewrites", "job_fit")) output.putArray(field);
        return output;
    }
    ObjectNode job(ObjectNode output) {
        var job = output.withArray("job_fit").addObject().put("slot", 0).put("relevance", "RELATED")
                .put("decision", "RECOMMENDED").put("decisionReason", "Java API 개발 경험이 연결됩니다.");
        job.putArray("criteria").addObject().put("jobQuoteId", "J0-P1").put("sourceId", "S1")
                .put("resumeQuoteId", "S1-P1").put("assessment", "SUPPORTED").put("explanation", "Java API 개발 경험을 확인했습니다.");
        return job;
    }
    ObjectNode validate(ObjectNode output) {
        return (ObjectNode) ResumeContentReviewRules.validate(output, sources, jobs, mapper).path("jobs").get(0);
    }

    @Test void omittedAndEmptyComparisonsAreNotApplicantInformationDeficits() {
        var output = output();
        var omitted = validate(output);
        assertThat(omitted.path("comparisonDiagnostics").path("status").asText()).isEqualTo("MODEL_OMITTED");
        assertThat(omitted.path("decision").asText()).isEqualTo("INSUFFICIENT_INFORMATION");
        job(output).putArray("criteria");
        assertThat(validate(output).path("comparisonDiagnostics").path("status").asText()).isEqualTo("EMPTY_COMPARISON");
    }

    @Test void invalidReferencesAndUnsupportedNumbersHaveDistinctNonSensitiveReasons() {
        for (String code : List.of("INVALID_JOB_REFERENCE", "INVALID_RESUME_REFERENCE", "UNSUPPORTED_NUMERIC_CLAIM")) {
            var output = output(); var job = job(output);
            var criterion = (ObjectNode) job.path("criteria").get(0);
            if (code.equals("INVALID_JOB_REFERENCE")) criterion.put("jobQuoteId", "unknown-private-value");
            if (code.equals("INVALID_RESUME_REFERENCE")) criterion.put("resumeQuoteId", "unknown-private-value");
            if (code.equals("UNSUPPORTED_NUMERIC_CLAIM")) criterion.put("explanation", "unknown-private-value 99%");
            var saved = validate(output); var diagnostic = saved.path("comparisonDiagnostics");
            assertThat(diagnostic.path("status").asText()).isEqualTo("VALIDATION_FAILED");
            assertThat(diagnostic.path("issues").path(code).asInt()).isEqualTo(1);
            assertThat(diagnostic.path("submittedCriteria").asInt()).isEqualTo(1);
            assertThat(diagnostic.path("acceptedCriteria").asInt()).isZero();
            assertThat(saved.toString()).doesNotContain("unknown-private-value", "99%");
            assertThat(saved.path("decision").asText()).isEqualTo("INSUFFICIENT_INFORMATION");
        }
    }

    @Test void acceptedCheckWithoutResumeCitationDoesNotMeanNoRegisteredExperience() {
        var output = output(); var job = job(output);
        job.put("decision", "INSUFFICIENT_INFORMATION").put("decisionReason", "해당 요건과 연결한 이력서 인용이 없습니다.");
        ((ObjectNode) job.path("criteria").get(0)).put("assessment", "CHECK").put("sourceId", "").put("resumeQuoteId", "");
        var saved = validate(output); var diagnostic = saved.path("comparisonDiagnostics");
        assertThat(diagnostic.path("status").asText()).isEqualTo("RESUME_EVIDENCE_NOT_CITED");
        assertThat(diagnostic.path("resumePassageCount").asInt()).isPositive();
        assertThat(diagnostic.path("acceptedCriteria").asInt()).isEqualTo(1);
        assertThat(diagnostic.path("uncitedResumeCriteria").asInt()).isEqualTo(1);
        assertThat(saved.path("criteria").get(0).path("jobQuote").asText()).isEqualTo("Java API 개발");
    }

    @Test void partialFailurePreservesValidEvidenceButCannotApproveTheJob() {
        var output = output(); var job = job(output);
        var invalid = job.path("criteria").get(0).deepCopy();
        ((ObjectNode) invalid).put("sourceId", "unknown-private-value");
        job.withArray("criteria").add(invalid);
        var saved = validate(output);
        assertThat(saved.path("criteria")).hasSize(1);
        assertThat(saved.path("comparisonDiagnostics").path("submittedCriteria").asInt()).isEqualTo(2);
        assertThat(saved.path("comparisonDiagnostics").path("acceptedCriteria").asInt()).isEqualTo(1);
        assertThat(saved.path("decision").asText()).isEqualTo("INSUFFICIENT_INFORMATION");
    }

    @Test void duplicateSlotsCannotApproveAndDecisionFailuresAreExplained() {
        var output = output(); var job = job(output);
        output.withArray("job_fit").add(job.deepCopy());
        assertThat(validate(output).path("comparisonDiagnostics").path("issues").path("DUPLICATE_JOB_SLOT").asInt()).isEqualTo(1);
        assertThat(validate(output).path("decision").asText()).isEqualTo("INSUFFICIENT_INFORMATION");
        output.withArray("job_fit").remove(1);
        job.put("decisionReason", "경력 99년으로 충분합니다.");
        assertThat(validate(output).path("comparisonDiagnostics").path("issues").path("DECISION_NOT_GROUNDED").asInt()).isEqualTo(1);
        job.put("decision", "invalid");
        assertThat(validate(output).path("comparisonDiagnostics").path("issues").path("INVALID_DECISION").asInt()).isEqualTo(1);
    }

    @Test void actualEmptyInputAndSuccessfulComparisonRemainDistinct() {
        var output = output(); job(output);
        var valid = validate(output);
        assertThat(valid.path("comparisonDiagnostics").path("status").asText()).isEqualTo("VALIDATED");
        assertThat(valid.path("decision").asText()).isEqualTo("RECOMMENDED");
        var emptyInput = ResumeContentReviewRules.validate(output, List.of(), jobs, mapper).path("jobs").get(0);
        assertThat(emptyInput.path("comparisonDiagnostics").path("status").asText()).isEqualTo("INPUT_MISSING");
        assertThat(emptyInput.path("comparisonDiagnostics").path("issues").path("NO_RESUME_PASSAGES").asInt()).isEqualTo(1);
        var missingRequirements = ResumeContentReviewRules.validate(output, sources,
                List.of(new ResumeContentReviewRules.Job(0, 17, "Java API 개발", false)), mapper).path("jobs").get(0);
        assertThat(missingRequirements.path("comparisonDiagnostics").path("status").asText()).isEqualTo("INPUT_MISSING");
        assertThat(missingRequirements.path("comparisonDiagnostics").path("issues").path("JOB_REQUIREMENTS_NOT_COLLECTED").asInt()).isEqualTo(1);
        assertThat(missingRequirements.path("decision").asText()).isEqualTo("INSUFFICIENT_INFORMATION");
    }
}
