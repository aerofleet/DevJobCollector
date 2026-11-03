package kr.itsdev.devjobcollector.collection.adapter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StartupSourceDiscoveryTest {
    @Test void discoversOnlyOfficialLinkMatchingStructuredPostingId() {
        String id = "4abd3a9b-b655-4095-a151-b6da2922f2f5";
        String html = """
            <a href="https://jobs.ashbyhq.com/friendliai/%s">공식 공고</a>
            <script type="application/ld+json">{"@type":"JobPosting","identifier":{"value":"%s"},"hiringOrganization":{"name":"프렌들리AI"}}</script>
            """.formatted(id, id);
        var candidate = StartupSourceDiscovery.candidate(html);
        assertThat(candidate).isPresent();
        assertThat(candidate.orElseThrow().board()).isEqualTo("friendliai");
        assertThat(candidate.orElseThrow().company()).isEqualTo("프렌들리AI");
        assertThat(StartupSourceDiscovery.candidate(html.replace("friendliai/" + id, "friendliai/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))).isEmpty();
        assertThat(StartupSourceDiscovery.candidate(html.replace("jobs.ashbyhq.com", "jobs.ashbyhq.com.example.org"))).isEmpty();
    }

    @Test void rejectsAmbiguousOrForeignLocations() {
        assertThat(StartupSourceDiscovery.koreanLocation("Seoul")).isTrue();
        assertThat(StartupSourceDiscovery.koreanLocation("Seoul, South Korea")).isTrue();
        assertThat(StartupSourceDiscovery.koreanLocation("Seoul / San Francisco")).isFalse();
        assertThat(StartupSourceDiscovery.koreanLocation(null)).isFalse();
    }
}
