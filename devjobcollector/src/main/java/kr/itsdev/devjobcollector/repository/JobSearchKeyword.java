package kr.itsdev.devjobcollector.repository;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JobSearchKeyword {

    static final int MAX_TOKENS = 8;
    private static final int MAX_TOKEN_LENGTH = 50;
    private static final String TOKEN_SEPARATOR = "[\\s\\-./_·,]+";
    private static final List<List<String>> SYNONYM_GROUPS = List.of(
            List.of("백엔드", "backend", "back-end", "server"),
            List.of("프론트엔드", "frontend", "front-end"),
            List.of("풀스택", "fullstack", "full-stack", "full stack"),
            List.of("스프링", "spring", "springboot", "spring boot", "spring-boot"),
            List.of("쿠버네티스", "kubernetes", "k8s"),
            List.of("머신러닝", "machine learning", "ml"),
            List.of("인공지능", "artificial intelligence", "ai"),
            List.of("데브옵스", "devops", "sre"));
    private static final Map<String, String> PHRASE_ALIASES = Map.ofEntries(
            Map.entry("artificial intelligence", "인공지능"),
            Map.entry("machine learning", "머신러닝"),
            Map.entry("spring-boot", "스프링"),
            Map.entry("spring boot", "스프링"),
            Map.entry("full-stack", "풀스택"),
            Map.entry("full stack", "풀스택"),
            Map.entry("front-end", "프론트엔드"),
            Map.entry("back-end", "백엔드"));
    private static final Map<String, List<String>> SYNONYMS_BY_TOKEN = buildSynonymIndex();

    private JobSearchKeyword() {
    }

    public static List<String> tokens(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Arrays.stream(normalized.split(TOKEN_SEPARATOR))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> token.length() > MAX_TOKEN_LENGTH
                        ? token.substring(0, MAX_TOKEN_LENGTH)
                        : token)
                .distinct()
                .limit(MAX_TOKENS)
                .forEach(tokens::add);
        return List.copyOf(tokens);
    }

    public static List<List<String>> termGroups(String keyword) {
        return tokens(normalizeKnownPhrases(keyword)).stream()
                .map(JobSearchKeyword::alternatives)
                .toList();
    }

    public static List<String> expandedTokens(String keyword) {
        return termGroups(keyword).stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }

    private static List<String> alternatives(String token) {
        return SYNONYMS_BY_TOKEN.getOrDefault(token, List.of(token));
    }

    private static String normalizeKnownPhrases(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return keyword;
        }
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> alias : PHRASE_ALIASES.entrySet()) {
            normalized = normalized.replace(alias.getKey(), alias.getValue());
        }
        return normalized;
    }

    private static Map<String, List<String>> buildSynonymIndex() {
        Map<String, List<String>> index = new HashMap<>();
        for (List<String> group : SYNONYM_GROUPS) {
            List<String> normalizedGroup = group.stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
            normalizedGroup.forEach(value -> index.put(value, normalizedGroup));
        }
        return Map.copyOf(index);
    }
}
