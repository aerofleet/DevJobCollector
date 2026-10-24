package kr.itsdev.devjobcollector.ai;

import java.util.*;
import java.util.regex.Pattern;

/** Shared role vocabulary for SQL retrieval and ranking; never an AI inference. */
final class RecommendationRoles {
    private RecommendationRoles() {}
    private static final Map<String, String> PATTERNS = Map.of(
            "백엔드", "백[ -]*엔드|back[ -]*end|서버[ -]*개발",
            "프론트엔드", "프론트[ -]*엔드|front[ -]*end",
            "풀스택", "풀[ -]*스택|full[ -]*stack",
            "데이터", "데이터|data[ -]*(engineer|scientist|analyst)",
            "인공지능", "인공지능|머신[ -]*러닝|machine[ -]*learning|(^|[^a-z])ai([^a-z]|$)",
            "모바일", "모바일|mobile|android|ios|안드로이드",
            "보안", "보안|security",
            "DevOps", "dev[ -]*ops|데브옵스|사이트[ -]*신뢰성|(^|[^a-z])sre([^a-z]|$)");

    static List<String> detect(String text) {
        if (text == null) return List.of();
        return PATTERNS.entrySet().stream()
                .filter(e -> Pattern.compile(e.getValue(), Pattern.CASE_INSENSITIVE).matcher(text).find())
                .map(Map.Entry::getKey).sorted().toList();
    }

    static String sqlPattern(String category) {
        return PATTERNS.getOrDefault(category, "a^");
    }

    static boolean matches(String actual, List<String> preferred) {
        if (actual == null) return false;
        var detected = detect(actual);
        return preferred.stream().anyMatch(p -> detected.contains(p)
                || actual.toLowerCase(Locale.ROOT).contains(p.toLowerCase(Locale.ROOT)));
    }
}
