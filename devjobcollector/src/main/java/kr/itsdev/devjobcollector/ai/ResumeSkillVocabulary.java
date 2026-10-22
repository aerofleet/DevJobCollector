package kr.itsdev.devjobcollector.ai;

import java.util.List;

/** Baseline names and aliases, independent of which job tags have been collected. */
final class ResumeSkillVocabulary {
    static boolean isNoise(String name) {
        return java.util.Set.of("테스트", "환경", "개발", "기술", "서버", "test", "testing", "environment", "backend", "frontend")
                .contains(name.strip().toLowerCase(java.util.Locale.ROOT));
    }
    static List<String> split(String name) { return java.util.Arrays.stream(name.split("[/,|]")).map(String::strip).filter(s -> !s.isBlank() && !isNoise(s)).toList(); }
    static boolean positiveMention(String text, int start, int end) {
        // Bound context at clauses so 'Kafka 대신 RabbitMQ 사용' keeps RabbitMQ only.
        int left = start, right = end;
        while (left > 0 && "\n.!?;".indexOf(text.charAt(left-1)) < 0) left--;
        while (right < text.length() && "\n.!?;".indexOf(text.charAt(right)) < 0) right++;
        String before = text.substring(left,start).toLowerCase(java.util.Locale.ROOT);
        String after = text.substring(end,right).toLowerCase(java.util.Locale.ROOT);
        String clause = text.substring(left,right).toLowerCase(java.util.Locale.ROOT);
        if (clause.matches("(?s).*(비교|검토|\\bvs\\b|compared).*" )
                && !clause.matches("(?s).*(구현|구축|운영|선택|채택|사용했|사용하|사용 중|implemented|deployed|used).*")) return false;
        if (after.matches("(?s)^\\s*(대신|보다|와의 비교|과의 비교).*")) return false;
        if (after.matches("(?s)^.{0,24}(미사용|미도입|사용하지|사용한 적|경험 없음|경험이 없|도입하지|비교만|검토만|학습 예정|배우고 싶).*")) return false;
        if (before.matches("(?s).*(미사용|미도입|비교 대상|검토 대상|not using|no experience with|compared (?:to|with))\\s*[:：]?\\s*")) return false;
        return !after.matches("(?s)^\\s*(?:was |is )?(?:not used|not adopted|unused).*");
    }
    static final List<String> NAMES = List.of(
            "Java", "JavaScript", "JS", "TypeScript", "TS", "Python", "Kotlin", "Go", "C++", "C#",
            "Spring", "Spring Boot", "스프링부트", "React", "React.js", "Node.js", "NodeJS", "Vue", "Next.js",
            "MySQL", "PostgreSQL", "Postgres", "MongoDB", "Redis", "RabbitMQ", "Kafka",
            "Docker", "Kubernetes", "AWS", "Azure", "GCP", "OCI", "Oracle Cloud Infrastructure",
            "Cloudflare", "Cloudflare Tunnel", "Nginx", "GitHub Actions", "Jenkins", "Terraform",
            "Prometheus", "Grafana", "Elasticsearch", "Linux", "Git");
    private ResumeSkillVocabulary() {}
}
