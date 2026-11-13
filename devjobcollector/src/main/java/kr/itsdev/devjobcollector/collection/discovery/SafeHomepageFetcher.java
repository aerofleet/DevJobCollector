package kr.itsdev.devjobcollector.collection.discovery;

import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import kr.itsdev.devjobcollector.collection.adapter.GreetingAdapter;
import kr.itsdev.devjobcollector.jobfile.PublicAddressResolverGroup;
import org.jsoup.Jsoup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Bounded, robots-aware homepage reader. DNS is validated by the connector at connection time. */
@Component
public class SafeHomepageFetcher {
    private static final String USER_AGENT = "DevJobCollector/1.0 (+https://itsdev.kr)";
    private static final int MAX_HTML_CHARS = 2_000_000;
    private static final long MIN_REQUEST_GAP_NANOS = Duration.ofMillis(500).toNanos();
    private static final Pattern IP_LITERAL = Pattern.compile("^(?:[0-9.]+|\\[[0-9a-fA-F:]+])$");
    private final WebClient web;
    private long nextRequestNanos;

    public SafeHomepageFetcher() {
        HttpClient client = HttpClient.create().resolver(new PublicAddressResolverGroup())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(8)).followRedirect(false);
        this.web = WebClient.builder().clientConnector(new ReactorClientHttpConnector(client))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_HTML_CHARS))
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity").build();
    }

    public List<Page> discoverablePages(String homepage) {
        URI root = validate(homepage);
        String robots = get(root.resolve("/robots.txt"), true).orElse(null);
        if (robots == null) return List.of();
        String rootPath = root.getRawPath() == null || root.getRawPath().isBlank() ? "/" : root.getRawPath();
        if (!GreetingAdapter.allowed(robots, rootPath)) return List.of();
        var home = get(root, false);
        if (home.isEmpty()) return List.of();

        Set<URI> pages = new LinkedHashSet<>();
        pages.add(root);
        var document = Jsoup.parse(home.get(), root.toString());
        document.select("a[href]").forEach(link -> {
            try {
                URI linked = validate(root.resolve(link.attr("href")).toString());
                if (sameOrigin(root, linked) && isCareersPath(linked.getRawPath())
                        && GreetingAdapter.allowed(robots, linked.getRawPath())) pages.add(linked);
            } catch (IllegalArgumentException ignored) { }
        });
        for (String path : List.of("/careers", "/career", "/jobs", "/recruit", "/recruitment", "/join", "/team")) {
            URI candidate = root.resolve(path);
            if (GreetingAdapter.allowed(robots, path)) pages.add(candidate);
        }
        List<Page> result = new ArrayList<>();
        int inspected = 0;
        for (URI page : pages) {
            if (inspected++ >= 8) break;
            if (page.equals(root)) result.add(new Page(page, home.get()));
            else get(page, false).ifPresent(html -> result.add(new Page(page, html)));
        }
        return List.copyOf(result);
    }

    URI validate(String value) {
        if (value == null || value.length() > 1000) throw new IllegalArgumentException("Invalid homepage URL");
        URI uri;
        try { uri = URI.create(value.trim()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid homepage URL", ex); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || !host.contains(".") || host.endsWith(".")
                || host.toLowerCase(Locale.ROOT).endsWith(".localhost")
                || host.toLowerCase(Locale.ROOT).endsWith(".local")
                || host.toLowerCase(Locale.ROOT).endsWith(".internal") || IP_LITERAL.matcher(host).matches())
            throw new IllegalArgumentException("Homepage URL is not allowed");
        return uri;
    }

    private java.util.Optional<String> get(URI uri, boolean robots) {
        try {
            paceRequests();
            return web.get().uri(uri).accept(robots ? MediaType.TEXT_PLAIN : MediaType.TEXT_HTML,
                            MediaType.valueOf("application/xhtml+xml"))
                    .exchangeToMono(response -> {
                        if (response.statusCode().value() == 404 && robots)
                            return Mono.just("User-agent: *\nAllow: /");
                        if (!response.statusCode().is2xxSuccessful()) return response.releaseBody().then(Mono.empty());
                        MediaType contentType = response.headers().contentType().orElse(null);
                        boolean accepted = contentType != null && (robots
                                ? MediaType.TEXT_PLAIN.isCompatibleWith(contentType)
                                : MediaType.TEXT_HTML.isCompatibleWith(contentType)
                                    || MediaType.valueOf("application/xhtml+xml").isCompatibleWith(contentType));
                        if (!accepted) return response.releaseBody().then(Mono.empty());
                        return response.bodyToMono(String.class);
                    }).timeout(Duration.ofSeconds(10)).blockOptional();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return java.util.Optional.empty();
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private synchronized void paceRequests() throws InterruptedException {
        long now = System.nanoTime();
        long wait = nextRequestNanos - now;
        while (wait > 0) {
            long millis = Math.max(1, Duration.ofNanos(wait).toMillis());
            Thread.sleep(millis);
            now = System.nanoTime();
            wait = nextRequestNanos - now;
        }
        nextRequestNanos = System.nanoTime() + MIN_REQUEST_GAP_NANOS;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getHost().equalsIgnoreCase(right.getHost()) && effectivePort(left) == effectivePort(right);
    }
    private static int effectivePort(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }
    private static boolean isCareersPath(String path) {
        String value = path.toLowerCase(Locale.ROOT);
        return value.matches(".*/(careers?|jobs?|recruit(?:ment)?|join|team)(?:/.*)?")
                || value.matches("/(careers?|jobs?|recruit(?:ment)?|join|team)");
    }

    public record Page(URI url, String html) { }
}
