package kr.itsdev.devjobcollector.ai;

import java.util.List;

/** Baseline names and aliases, independent of which job tags have been collected. */
final class ResumeSkillVocabulary {
    static final List<String> NAMES = List.of(
            "Java", "JavaScript", "JS", "TypeScript", "TS", "Python", "Kotlin", "Go", "C++", "C#",
            "Spring", "Spring Boot", "스프링부트", "React", "React.js", "Node.js", "NodeJS", "Vue", "Next.js",
            "MySQL", "PostgreSQL", "Postgres", "MongoDB", "Redis", "RabbitMQ", "Kafka",
            "Docker", "Kubernetes", "AWS", "Azure", "GCP", "OCI", "Oracle Cloud Infrastructure",
            "Cloudflare", "Cloudflare Tunnel", "Nginx", "GitHub Actions", "Jenkins", "Terraform",
            "Prometheus", "Grafana", "Elasticsearch", "Linux", "Git");
    private ResumeSkillVocabulary() {}
}
