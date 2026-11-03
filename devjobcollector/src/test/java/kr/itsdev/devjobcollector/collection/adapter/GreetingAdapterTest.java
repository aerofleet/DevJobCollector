package kr.itsdev.devjobcollector.collection.adapter;

import org.junit.jupiter.api.Test;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import static org.assertj.core.api.Assertions.assertThat;

class GreetingAdapterTest {
    @Test void collectsPublishedListAndPermitsClosureOnlyWhenRenderedListMatches() throws Exception {
        String html = """
            <a data-testid="공고_아이템" href="/ko/o/123">개발자</a>
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"dehydratedState":{"queries":[{"queryKey":["openings"],"state":{"data":[{"deploy":true,"openingId":123,"title":"개발자","openDate":"2026-01-01T00:00:00Z","dueDate":"2026-12-01T00:00:00Z"}]}}]}}}}</script>
            """;
        var result = GreetingAdapter.parseList(html, "https://nuua.career.greetinghr.com", "User-agent: *\nAllow: /");
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.closureEvaluationAllowed()).isTrue();
        assertThat(result.jobs()).singleElement().satisfies(job -> {
            assertThat(job.title()).isEqualTo("개발자");
            assertThat(job.deadlineAt()).isNotNull();
        });
        var mismatch = GreetingAdapter.parseList(html.replace("/ko/o/123", "/ko/o/456"), "https://nuua.career.greetinghr.com", "User-agent: *\nAllow: /");
        assertThat(mismatch.status()).isEqualTo(CollectionStatus.SCHEMA_CHANGED);
        assertThat(mismatch.closureEvaluationAllowed()).isFalse();
    }
    @Test void acceptsOnlyPostingUrlsOnTheConfiguredBoard() {
        String xml = """
                <urlset><url><loc>https://nuua.career.greetinghr.com/o/123</loc></url>
                <url><loc>https://nuua.career.greetinghr.com/o/123</loc></url>
                <url><loc>https://evil.example/o/456</loc></url>
                <url><loc>https://nuua.career.greetinghr.com/o/123/apply</loc></url></urlset>
                """;
        assertThat(GreetingAdapter.postingUrls(xml, "https://nuua.career.greetinghr.com"))
                .containsExactly("https://nuua.career.greetinghr.com/o/123");
    }

    @Test void respectsRobotsRulesAndSearchProhibition() {
        String robots = "User-agent: *\nAllow: /\nDisallow: /o/*/apply\n";
        assertThat(GreetingAdapter.allowed(robots, "/o/123")).isTrue();
        assertThat(GreetingAdapter.allowed(robots, "/o/123/apply")).isFalse();
        assertThat(GreetingAdapter.allowed("User-agent: *\nContent-Signal: search=no\nAllow: /", "/o/123")).isFalse();
        assertThat(GreetingAdapter.allowed("", "/o/123")).isFalse();
    }
}
