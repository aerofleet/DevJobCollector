package kr.itsdev.devjobcollector.collection.discovery;

import kr.itsdev.devjobcollector.collection.domain.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtsFingerprintServiceTest {
    private final AtsFingerprintService service = new AtsFingerprintService();

    @Test
    void identifiesSupportedBoardsAndJobLinks() {
        assertThat(service.identify("https://nuua.career.greetinghr.com/ko/home"))
                .contains(new AtsFingerprintService.Fingerprint(SourceType.GREETING, "nuua"));
        assertThat(service.identify("https://jobs.lever.co/example/123?source=site"))
                .contains(new AtsFingerprintService.Fingerprint(SourceType.LEVER, "example"));
        assertThat(service.identify("https://job-boards.greenhouse.io/example/jobs/123"))
                .contains(new AtsFingerprintService.Fingerprint(SourceType.GREENHOUSE, "example"));
        assertThat(service.identify("https://jobs.ashbyhq.com/example/123"))
                .contains(new AtsFingerprintService.Fingerprint(SourceType.ASHBY, "example"));
        assertThat(service.identify("https://jobs.ashbyhq.com/FriendliAI/123"))
                .contains(new AtsFingerprintService.Fingerprint(SourceType.ASHBY, "FriendliAI"));
    }

    @Test
    void rejectsSpoofedHostsAndAmbiguousIdentifiers() {
        for (String url : new String[] {"https://jobs.ashbyhq.com.evil.test/example",
                "https://jobs.ashbyhq.com@evil.test/example", "http://jobs.lever.co/example",
                "https://jobs.lever.co:8443/example", "https://jobs.lever.co/%65xample",
                "https://a.b.career.greetinghr.com/", "https://bad_name.career.greetinghr.com/",
                "https://jobs.lever.co/", "not a url"}) {
            assertThat(service.identify(url)).as(url).isEmpty();
        }
    }
}
