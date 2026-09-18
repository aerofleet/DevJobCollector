package kr.itsdev.devjobcollector.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobSearchKeywordTest {

    @Test
    void normalizesCaseWidthAndDeveloperSeparators() {
        assertThat(JobSearchKeyword.tokens("  ＪＡＶＡ/Spring-Boot · 신입  "))
                .containsExactly("java", "spring", "boot", "신입");
    }

    @Test
    void keepsDeveloperLanguageSymbols() {
        assertThat(JobSearchKeyword.tokens("C++ C# Node.js"))
                .containsExactly("c++", "c#", "node", "js");
    }

    @Test
    void removesDuplicatesWhileKeepingInputOrder() {
        assertThat(JobSearchKeyword.tokens("Java java JAVA Spring"))
                .containsExactly("java", "spring");
    }

    @Test
    void limitsQueryComplexityToEightTokens() {
        assertThat(JobSearchKeyword.tokens("1 2 3 4 5 6 7 8 9 10"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    void duplicateTokensDoNotConsumeTokenLimit() {
        assertThat(JobSearchKeyword.tokens("java java java java java java java java spring kafka"))
                .containsExactly("java", "spring", "kafka");
    }

    @Test
    void returnsNoTokensForBlankInput() {
        assertThat(JobSearchKeyword.tokens("  / - .  ")).isEmpty();
    }

    @Test
    void expandsDeveloperSynonymsWithinOneAndTerm() {
        assertThat(JobSearchKeyword.termGroups("백엔드 k8s"))
                .containsExactly(
                        java.util.List.of("백엔드", "backend", "back-end", "server"),
                        java.util.List.of("쿠버네티스", "kubernetes", "k8s"));
    }

    @Test
    void keepsMultiWordAndHyphenatedAliasesInOneAndTerm() {
        assertThat(JobSearchKeyword.termGroups("artificial intelligence back-end"))
                .containsExactly(
                        java.util.List.of("인공지능", "artificial intelligence", "ai"),
                        java.util.List.of("백엔드", "backend", "back-end", "server"));
    }
}
