package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;

/** Senior-role signals require review; suitability remains an evidence-based model decision. */
final class AiJobFit {
    static final String VERSION = "ai-job-fit-v6-evidence-policy";
    private static final Pattern SENIOR_ROLE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])(?:senior|sr\\.?|staff|principal|lead|vice[\\s-]+president|vp|director|manager)(?![\\p{L}\\p{N}_])|시니어|수석");

    static boolean requiresSeniorityReview(Map<String, Object> job) {
        // Inspect role fields, not vendor names or incidental mentions of senior colleagues in the body.
        return List.of("title", "experience", "job_category").stream()
                .anyMatch(field -> SENIOR_ROLE.matcher(Objects.toString(job.get(field), "")).find());
    }
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
