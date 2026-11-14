package kr.itsdev.devjobcollector.collection.discovery;

import kr.itsdev.devjobcollector.collection.domain.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HomepageAtsLinkDiscoveryTest {
    private final HomepageAtsLinkDiscovery discovery = new HomepageAtsLinkDiscovery(new AtsFingerprintService());

    @Test
    void extractsDistinctSupportedLinksWithoutTrustingBaseElement() {
        String html = """
                <base href="https://jobs.lever.co/">
                <a href="/fake">relative</a>
                <a href="https://nuua.career.greetinghr.com/ko/home">careers</a>
                <a href="https://nuua.career.greetinghr.com/ko/o/123">job</a>
                <a href="https://jobs.ashbyhq.com/FriendliAI">ashby</a>
                <a href="https://jobs.lever.co.evil.test/foo">spoof</a>
                """;
        var candidates = discovery.discover("https://company.example/", html);
        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting(c -> c.fingerprint().provider())
                .containsExactly(SourceType.GREETING, SourceType.ASHBY);
        assertThat(candidates.get(1).fingerprint().sourceIdentifier()).isEqualTo("FriendliAI");
    }

    @Test
    void rejectsInvalidHomepageAndEmptyInput() {
        assertThat(discovery.discover("http://company.example", "<a href='https://jobs.lever.co/example'>jobs</a>"))
                .isEmpty();
        assertThat(discovery.discover(null, "")).isEmpty();
    }

    @Test
    void recognizesTheSuppliedGreetingBoardBeforeItsOtherLinks() {
        String board = "https://cnccosmetic.career.greetinghr.com/ko/apply";
        var candidates = discovery.discover(board, "<a href='/ko'>회사 소개</a><a href='/ko/o/123'>공고</a>");
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.fingerprint().provider()).isEqualTo(SourceType.GREETING);
            assertThat(candidate.fingerprint().sourceIdentifier()).isEqualTo("cnccosmetic");
            assertThat(candidate.sourceUrl()).isEqualTo(board);
        });
    }

    @Test
    void selectsOnlySupportedGreetingListPaths() {
        var fingerprints = new AtsFingerprintService();
        assertThat(fingerprints.greetingBoardUrl("https://cnccosmetic.career.greetinghr.com/ko/apply", "cnccosmetic"))
                .isEqualTo("https://cnccosmetic.career.greetinghr.com/ko/apply");
        assertThat(fingerprints.greetingBoardUrl("https://nuua.career.greetinghr.com/ko/o/123", "nuua"))
                .isEqualTo("https://nuua.career.greetinghr.com/ko/home");
    }
}
