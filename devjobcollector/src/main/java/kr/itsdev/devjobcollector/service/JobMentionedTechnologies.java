package kr.itsdev.devjobcollector.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;

/** Recognized technologies explicitly present in the collected posting text. */
public final class JobMentionedTechnologies {
    private static final List<String> NAMES = List.of(
            "Google apps", "JIRA", "Salesforce Service Cloud", "Amazon Connect", "LibreChat", "Gemini", "Glean",
            "Java", "Kotlin", "JavaScript", "TypeScript", "Python", "C++", "ROS2", "PyTorch", "JAX", "LeRobot",
            "MuJoCo", "Isaac Sim", "React", "Spring", "JPA", "Hibernate", "Gradle", "Jenkins", "Git", "Istio",
            "Kafka", "Elastic", "InfluxData", "Grafana", "Memcached", "Cloud Native", "iOS", "SQL", "Excel",
            "SAP", "RDBMS", "NoSQL", "PostgreSQL", "MongoDB", "Docker", "Kubernetes", "AWS", "Azure", "GCP",
            "IaC", "CI/CD");
    private static final Map<String, String> CANONICAL = NAMES.stream().collect(Collectors.toUnmodifiableMap(
            name -> name.toLowerCase(Locale.ROOT), name -> name));
    private static final Pattern NAMES_PATTERN = Pattern.compile("(?<![\\w+])(?:"
            + NAMES.stream().sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .map(Pattern::quote).collect(Collectors.joining("|"))
            + ")(?![\\w+])", Pattern.CASE_INSENSITIVE);
    private static final Pattern LLM = Pattern.compile("\\b(?:large language models?|LLMs?)\\b", Pattern.CASE_INSENSITIVE);

    private JobMentionedTechnologies() {}

    public static List<String> from(String content) {
        if (content == null || content.isBlank()) return List.of();
        String text = Jsoup.parse(content).text();
        Map<String, String> found = new LinkedHashMap<>();
        var matcher = NAMES_PATTERN.matcher(text);
        while (matcher.find()) {
            String key = matcher.group().toLowerCase(Locale.ROOT);
            found.putIfAbsent(key, CANONICAL.get(key));
        }
        if (LLM.matcher(text).find()) found.putIfAbsent("llm", "LLM");
        return List.copyOf(found.values());
    }
}
