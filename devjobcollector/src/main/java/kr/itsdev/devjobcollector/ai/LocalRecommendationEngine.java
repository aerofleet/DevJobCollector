package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalRecommendationEngine {
    public static final String VERSION = "local-tags-title-v3";
    private final double skillWeight, careerWeight, categoryWeight, locationWeight, employmentWeight, freshnessWeight;

    public LocalRecommendationEngine(
            @Value("${ai.ranking.weights.skill:30}") double skill,
            @Value("${ai.ranking.weights.career:15}") double career,
            @Value("${ai.ranking.weights.category:10}") double category,
            @Value("${ai.ranking.weights.location:10}") double location,
            @Value("${ai.ranking.weights.employment:5}") double employment,
            @Value("${ai.ranking.weights.freshness:5}") double freshness) {
        for (double weight : new double[]{skill, career, category, location, employment, freshness}) {
            if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid ranking weight");
        }
        if (skill + career + category + location + employment + freshness <= 0) throw new IllegalArgumentException("Empty weights");
        skillWeight = skill; careerWeight = career; categoryWeight = category;
        locationWeight = location; employmentWeight = employment; freshnessWeight = freshness;
    }

    public String algorithmVersion() {
        return VERSION + ":" + skillWeight + ":" + careerWeight + ":" + categoryWeight + ":"
                + locationWeight + ":" + employmentWeight + ":" + freshnessWeight;
    }

    public Set<String> skills(JsonNode content) {
        var skills = new TreeSet<String>();
        for (JsonNode item : content.path("techStack")) {
            String name = item.isTextual() ? item.asText() : item.path("name").asText("");
            if (!name.isBlank() && name.length() <= 50) skills.add(normalizeSkill(name));
            if (skills.size() > 50) throw ResumeTextExtractor.failure(org.springframework.http.HttpStatus.BAD_REQUEST, "TOO_MANY_SKILLS");
        }
        return Collections.unmodifiableSet(skills);
    }

    public static String normalizeSkill(String value) {
        String text = value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
        return switch (text) {
            case "springboot", "스프링부트" -> "springboot";
            case "javascript", "js" -> "javascript";
            case "typescript", "ts" -> "typescript";
            case "node.js", "nodejs" -> "nodejs";
            case "react.js", "reactjs", "react" -> "react";
            case "postgres", "postgresql" -> "postgresql";
            default -> text;
        };
    }

    public List<Recommendation> rank(Set<String> resumeSkills, Preferences preferences,
                                     List<Candidate> candidates, LocalDate asOf) {
        double total = skillWeight + careerWeight + categoryWeight + locationWeight + employmentWeight + freshnessWeight;
        var ranked = new ArrayList<Recommendation>();
        for (Candidate job : candidates) {
            var requirements = new TreeSet<String>();
            job.skills().forEach(skill -> requirements.add(normalizeSkill(skill)));
            var matching = new TreeSet<>(requirements);
            matching.retainAll(resumeSkills);
            var missing = new TreeSet<>(requirements);
            missing.removeAll(resumeSkills);
            boolean categoryMatch = matches(job.category(), preferences.category());
            // Freshness alone never qualifies an unrelated job.
            if (matching.isEmpty() && !categoryMatch) continue;
            double exact = requirements.isEmpty() ? 0 : (double) matching.size() / requirements.size();
            Double career = careerFit(job.experience(), preferences.careerYears());
            double fresh = job.createdDate() == null ? 0 : Math.max(0, 1.0 - Math.max(0,
                    ChronoUnit.DAYS.between(job.createdDate(), asOf)) / 30.0);
            var components = new LinkedHashMap<String, Double>();
            components.put("skillExactMatch", exact * skillWeight);
            components.put("careerFit", career == null ? 0 : career * careerWeight);
            components.put("jobCategory", categoryMatch ? categoryWeight : 0);
            components.put("locationFit", matches(job.location(), preferences.location()) ? locationWeight : 0);
            components.put("employmentType", matches(job.employmentType(), preferences.employmentType()) ? employmentWeight : 0);
            components.put("jobFreshness", fresh * freshnessWeight);
            double score = Math.round(components.values().stream().mapToDouble(Double::doubleValue).sum() / total * 10000) / 100.0;
            var unknown = new ArrayList<String>();
            unknown.add("semanticSimilarity");
            if (requirements.isEmpty() || job.titleEvidence()) unknown.add("requiredSkills");
            if (career == null) unknown.add("careerFit");
            if (job.createdDate() == null) unknown.add("jobFreshness");
            ranked.add(new Recommendation(job.id(), 0, score, List.copyOf(matching), List.copyOf(missing),
                    Map.copyOf(components), List.copyOf(unknown), job.titleEvidence() ? "JOB_TITLE" : "TECH_TAGS"));
        }
        ranked.sort(Comparator.comparingDouble(Recommendation::matchScore).reversed().thenComparingLong(Recommendation::jobId));
        var result = new ArrayList<Recommendation>();
        for (var value : ranked.subList(0, Math.min(5, ranked.size()))) {
            result.add(new Recommendation(value.jobId(), result.size() + 1, value.matchScore(),
                    value.matchPoints(), value.missingPoints(), value.components(), value.unknownFactors(), value.skillEvidenceSource()));
        }
        return List.copyOf(result);
    }

    private static boolean matches(String actual, List<String> preferred) {
        return actual != null && preferred.stream().anyMatch(value ->
                actual.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)));
    }

    static Double careerFit(String text, Integer years) {
        if (text == null || years == null) return null;
        if (text.contains("무관")) return 1.0;
        if (text.contains("신입") && text.contains("경력")) return 1.0;
        if (text.contains("신입")) return years == 0 ? 1.0 : 0.0;
        var range = Pattern.compile("(\\d{1,2})\\s*년?\\s*[~～–-]\\s*(\\d{1,2})\\s*년").matcher(text);
        if (range.find()) {
            int min = Integer.parseInt(range.group(1)), max = Integer.parseInt(range.group(2));
            return years >= min && years <= max ? 1.0 : 0.0;
        }
        var limit = Pattern.compile("(\\d{1,2})\\s*년\\s*(이상|이하|미만)").matcher(text);
        if (limit.find()) {
            int value = Integer.parseInt(limit.group(1));
            return switch (limit.group(2)) {
                case "이상" -> years >= value ? 1.0 : 0.0;
                case "이하" -> years <= value ? 1.0 : 0.0;
                default -> years < value ? 1.0 : 0.0;
            };
        }
        return null;
    }

    public List<Improvement> review(JsonNode content) {
        var issues = new ArrayList<Improvement>();
        if (skills(content).isEmpty()) issues.add(new Improvement("/techStack", "MISSING_SKILLS", "사용한 기술 스택을 추가해 주세요."));
        if (!content.path("projects").isArray() || content.path("projects").isEmpty()) {
            issues.add(new Improvement("/projects", "MISSING_PROJECT", "프로젝트에서 맡은 역할과 결과를 작성해 주세요."));
        }
        int index = 0;
        for (JsonNode project : content.path("projects")) {
            String path = "/projects/" + index++ + "/description";
            String description = java.util.stream.Stream.of("description", "problem", "role", "actions", "outcome", "evidence")
                    .map(field -> project.path(field).asText("")).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining("\n"));
            if (description.isBlank()) {
                issues.add(new Improvement(path, "MISSING_DESCRIPTION", "문제, 본인의 역할, 수행한 작업과 결과를 작성해 주세요."));
            } else if (!Pattern.compile("\\d").matcher(description).find()) {
                issues.add(new Improvement(path, "METRIC_NOT_DETECTED", "검증 가능한 수치가 있다면 측정 조건과 함께 추가해 주세요. 수치는 임의로 만들지 마세요."));
            }
            if (issues.size() >= 20) break;
        }
        return List.copyOf(issues);
    }

    public record Preferences(Integer careerYears, List<String> category, List<String> location, List<String> employmentType) {
        public Preferences {
            category = normalize(category); location = normalize(location); employmentType = normalize(employmentType);
        }
        public Preferences(Integer careerYears, String category, String location, String employmentType) {
            this(careerYears, single(category), single(location), single(employmentType));
        }
        private static List<String> single(String value) { return value == null ? List.of() : List.of(value); }
        private static List<String> normalize(List<String> values) {
            return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim)
                    .filter(value -> !value.isEmpty()).distinct().sorted().toList();
        }
    }
    public record Candidate(long id, String category, String experience, String location, String employmentType,
                            LocalDate createdDate, List<String> skills, boolean titleEvidence) {
        public Candidate(long id, String category, String experience, String location, String employmentType,
                         LocalDate createdDate, List<String> skills) {
            this(id, category, experience, location, employmentType, createdDate, skills, false);
        }
    }
    public record Recommendation(long jobId, int rank, double matchScore, List<String> matchPoints,
                                 List<String> missingPoints, Map<String, Double> components, List<String> unknownFactors,
                                 String skillEvidenceSource) {}
    public record Improvement(String sourcePath, String issueType, String feedback) {}
}
