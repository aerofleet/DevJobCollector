package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Reuse only model decisions tied to the exact reviewed inputs. No seniority heuristics. */
final class AiJobFit {
    static final String VERSION = "ai-job-fit-v1";
    static String jobFingerprint(Map<String, Object> job) {
        var value = new StringBuilder();
        for (String field : List.of("title", "job_category", "experience", "location", "hire_type", "qualifications")) {
            String text = Objects.toString(job.get(field), "");
            value.append(text.length()).append(':').append(text);
        }
        return ResumeAnalysisService.hash(value.toString());
    }
    static String decision(JsonNode review, String inputFingerprint, String jobFingerprint) {
        if (!VERSION.equals(review.path("fitVersion").asText())
                || !inputFingerprint.equals(review.path("inputFingerprint").asText())
                || !jobFingerprint.equals(review.path("jobFingerprint").asText())) return "REVIEW_REQUIRED";
        String decision = review.path("decision").asText();
        return Set.of("RECOMMENDED", "STRETCH", "NOT_RECOMMENDED", "INSUFFICIENT_INFORMATION").contains(decision)
                ? decision : "REVIEW_REQUIRED";
    }
    static boolean visible(String decision) { return "RECOMMENDED".equals(decision) || "STRETCH".equals(decision); }
    private AiJobFit() {}
}
