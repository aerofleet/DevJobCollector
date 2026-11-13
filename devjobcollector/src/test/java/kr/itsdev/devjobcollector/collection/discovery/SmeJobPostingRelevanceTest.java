package kr.itsdev.devjobcollector.collection.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmeJobPostingRelevanceTest {
    private final SmeJobPostingRelevance classifier = new SmeJobPostingRelevance();

    @Test
    void prioritizesDeveloperAndDataRolesButLeavesGeneralOfficeRolesUnflagged() {
        assertThat(classifier.isRelevant("백엔드 개발자 (Java)" )).isTrue();
        assertThat(classifier.isRelevant("데이터 분석 엔지니어")).isTrue();
        assertThat(classifier.isRelevant("총무 및 회계 담당자")).isFalse();
        assertThat(classifier.isRelevant(null)).isFalse();
    }
}
