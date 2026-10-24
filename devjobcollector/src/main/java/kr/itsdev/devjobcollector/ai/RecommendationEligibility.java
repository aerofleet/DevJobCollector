package kr.itsdev.devjobcollector.ai;

import java.util.regex.Pattern;

/** Product eligibility rules, not inferred industry-wide definitions of seniority. */
final class RecommendationEligibility {
    private RecommendationEligibility() {}
    private static final Pattern YEARS = Pattern.compile(
            "(?i)(?<![\\d.])(\\d{1,2})(?:\\s*(?:년|years?)?\\s*[-~～–]\\s*\\d{1,2})?\\s*(?:\\+\\s*)?(?:년|years?|yrs?)(?!차)(?:\\s*(이상|이하|미만|이내))?");
    private static boolean has(String text, String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
    }
    static Integer minimumYears(String text) {
        if (text == null) return null;
        var matcher = YEARS.matcher(text);
        Integer minimum = null;
        while (matcher.find()) {
            if (matcher.group(2) != null && !"이상".equals(matcher.group(2))) continue;
            int value = Integer.parseInt(matcher.group(1));
            if (value <= 60) minimum = minimum == null ? value : Math.max(minimum, value);
        }
        return minimum;
    }
    static Decision assess(LocalRecommendationEngine.Candidate job, Integer years) {
        String title = job.title() == null ? "" : job.title();
        // Vendor/product names in a CV are not evidence of fit for commercial roles.
        if (has(title, "\\b(sales (engineer|manager|representative|executive)|account executive|gtm|recruiter|business development|product manager)\\b|영업[ -]*(담당|매니저|사원|엔지니어)|채용담당")) {
            return new Decision(false, "NON_DEVELOPMENT_ROLE", null, "JOB_TITLE");
        }
        boolean senior = has(title, "\\b(senior|sr\\.?|staff|principal|lead|distinguished)\\b|시니어|수석|책임|리드|테크리더|팀장|\\bengineering manager\\b");
        boolean mixedLevel = senior && has(title, "\\b(junior|entry[- ]level|all levels)\\b|주니어|신입|전직급");
        Integer titleMinimum = minimumYears(title);
        Integer fieldMinimum = minimumYears(job.experience());
        Integer minimum = titleMinimum;
        String source = titleMinimum == null ? "EXPERIENCE_FIELD" : "JOB_TITLE";
        if (senior && !mixedLevel && titleMinimum == null) {
            minimum = has(title, "\\b(principal|distinguished)\\b|수석") ? 8
                    : has(title, "\\bstaff\\b") ? 7 : 5;
            source = "TITLE_SENIORITY_POLICY";
        }
        if (fieldMinimum != null && (minimum == null || fieldMinimum > minimum)) {
            minimum = fieldMinimum; source = "EXPERIENCE_FIELD";
        }
        if (years != null && minimum != null && years < minimum) {
            return new Decision(false, "INSUFFICIENT_CAREER", minimum, source);
        }
        if (years == null && senior && !mixedLevel) {
            return new Decision(false, "CAREER_REQUIRED_FOR_SENIOR", minimum, source);
        }
        return new Decision(true, years == null ? "CAREER_UNCONFIRMED" : "ELIGIBLE", minimum, source);
    }
    record Decision(boolean eligible, String reason, Integer minimumYears, String source) {}
}
