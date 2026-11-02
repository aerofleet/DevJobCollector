package kr.itsdev.devjobcollector.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JobMentionedTechnologiesTest {
    @Test
    void extractsExplicitNamesFromPostingWithoutSubstringMatches() {
        String text = "<h3>팀 소개</h3><p>Kubernetes와 Cloud Native 활용</p>\n"
                + "AWS 및 CI/CD 경험\n# 토스에서 사용하는 기술\n"
                + "Java Spring Framework, JPA/Hibernate, Gradle\n"
                + "Jenkins, Git, Docker, Kubernetes + Istio\n"
                + "Kafka, Elastic, InfluxData, Grafana, Memcached";
        assertThat(JobMentionedTechnologies.from(text)).containsExactlyInAnyOrder(
                "Kubernetes", "Cloud Native", "AWS", "CI/CD", "Java", "Spring", "JPA", "Hibernate", "Gradle",
                "Jenkins", "Git", "Docker", "Istio", "Kafka", "Elastic", "InfluxData", "Grafana", "Memcached");
        assertThat(JobMentionedTechnologies.from("JavaScript, GitLab, PostgreSQL")).containsExactly("JavaScript", "PostgreSQL");
        assertThat(JobMentionedTechnologies.from(null)).isEmpty();
    }
}
