package kr.itsdev.devjobcollector.collection.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SafeHomepageFetcherTest {
    private final SafeHomepageFetcher fetcher = new SafeHomepageFetcher();

    @Test
    void acceptsOnlyPublicHttpsStyleHomepageUris() {
        assertThat(fetcher.validate("https://example.kr/about").toString()).isEqualTo("https://example.kr/about");
        for (String url : new String[]{"http://example.kr", "https://localhost", "https://127.0.0.1",
                "https://[::1]", "https://user@example.kr", "https://example.kr:8443", "https://example.kr/#x"}) {
            assertThatThrownBy(() -> fetcher.validate(url)).as(url).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void robotsPolicyHonorsAllowAndDisallowRules() {
        String robots = "User-agent: *\nAllow: /\nDisallow: /careers/private\n";
        assertThat(kr.itsdev.devjobcollector.collection.adapter.GreetingAdapter.allowed(robots, "/"))
                .isTrue();
        assertThat(kr.itsdev.devjobcollector.collection.adapter.GreetingAdapter.allowed(robots, "/careers/private"))
                .isFalse();
    }
}
