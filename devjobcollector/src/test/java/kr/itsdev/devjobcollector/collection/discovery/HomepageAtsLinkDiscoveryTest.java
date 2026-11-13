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
}
