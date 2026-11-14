package kr.itsdev.devjobcollector.collection.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SafeHomepageFetcherDirectBoardTest {
    @Test void readsOnlyRobotsAndTheSuppliedSupportedBoard() {
        var paths = new ArrayList<String>();
        var web = WebClient.builder().exchangeFunction(request -> {
            paths.add(request.url().getRawPath());
            boolean robots = request.url().getRawPath().equals("/robots.txt");
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, robots ? "text/plain" : "text/html")
                    .body(robots ? "User-agent: *\nAllow: /" : "<a href='/ko/o/123'>공고</a>").build());
        }).build();
        var pages = new SafeHomepageFetcher(web).discoverablePages("https://cnccosmetic.career.greetinghr.com/ko/apply");
        assertThat(pages).hasSize(1);
        assertThat(paths).containsExactly("/robots.txt", "/ko/apply");
    }

    @Test void doesNotBypassRobotsForAnExplicitBoard() {
        var paths = new ArrayList<String>();
        var web = WebClient.builder().exchangeFunction(request -> {
            paths.add(request.url().getRawPath());
            return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, "text/plain")
                    .body("User-agent: *\nDisallow: /ko/apply").build());
        }).build();
        assertThat(new SafeHomepageFetcher(web).discoverablePages("https://cnccosmetic.career.greetinghr.com/ko/apply")).isEmpty();
        assertThat(paths).containsExactly("/robots.txt");
    }
}
