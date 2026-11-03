package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.AtsCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;

/** Discovers candidates in a public index and verifies them against the official board. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSourceDiscovery {
    private static final String ORIGIN = "https://itchaeyong.com";
    private static final String AGENT = "DevJobCollector/1.0 (+https://itsdev.kr)";
    private static final Pattern DETAIL = Pattern.compile("/jobs/[0-9a-fA-F-]{36}");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final AtsCollectionProperties properties;
    private final CompanySourceTargetRepository targets;
    private final AshbyAdapter ashby;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "startup-source-discovery");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    @Value("${collection.discovery.enabled:true}") private boolean enabled;

    @Scheduled(initialDelayString = "${collection.discovery.initial-delay-ms:120000}",
            fixedDelayString = "${collection.discovery.poll-delay-ms:86400000}")
    public void discover() {
        if (!enabled || !properties.enabled()) return;
        if (!running.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try { crawl(); } finally { running.set(false); }
        });
    }

    @PreDestroy void shutdown() { executor.shutdownNow(); }

    private void crawl() {
        try {
            String robots = get(ORIGIN + "/robots.txt");
            if (robots == null || !GreetingAdapter.allowed(robots, "/")) return;
            String html = get(ORIGIN + "/");
            if (html == null) return;
            Set<String> paths = new LinkedHashSet<>();
            Jsoup.parse(html).select("a[href]").forEach(link -> {
                String path = link.attr("href");
                if (DETAIL.matcher(path).matches()) paths.add(path);
            });
            int inspected = 0, added = 0;
            for (String path : paths) {
                if (inspected++ >= 20) break;
                if (!GreetingAdapter.allowed(robots, path)) continue;
                Thread.sleep(500);
                String detail = get(ORIGIN + path);
                if (detail == null) continue;
                Optional<Candidate> candidate = candidate(detail);
                if (candidate.isEmpty()) continue;
                Candidate c = candidate.get();
                if (targets.findByProviderAndSourceIdentifier(SourceType.ASHBY, c.board()).isPresent()) continue;
                CompanySourceTarget target = new CompanySourceTarget(null, c.company(), SourceType.ASHBY,
                        c.board(), "https://jobs.ashbyhq.com/" + c.board(), CollectionTier.B);
                var result = ashby.fetchJobs(target, CollectionContext.now());
                if (result.jobs().isEmpty() || !result.closureEvaluationAllowed()) continue;
                boolean matchingJob = result.jobs().stream().anyMatch(job -> c.jobUrl().equals(job.sourceUrl()));
                // Require every job to have a clearly Korean location before importing the entire board.
                boolean koreanBoard = result.jobs().stream().allMatch(job -> koreanLocation(job.location()));
                if (!matchingJob || !koreanBoard) continue;
                target.activate();
                targets.save(target);
                added++;
            }
            log.info("Startup source discovery completed: examined={}, added={}", Math.min(inspected, 20), added);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Startup source discovery failed: {}", e.getClass().getSimpleName());
        }
    }

    static Optional<Candidate> candidate(String html) {
        try {
            var doc = Jsoup.parse(html);
            Set<String> urls = new LinkedHashSet<>();
            doc.select("a[href]").forEach(a -> {
                String href = a.attr("href");
                if (href.matches("https://jobs\\.ashbyhq\\.com/[A-Za-z0-9_-]+/[0-9a-fA-F-]{36}")) urls.add(href);
            });
            for (var script : doc.select("script[type=application/ld+json]")) {
                JsonNode data = MAPPER.readTree(script.data());
                if (!"JobPosting".equals(data.path("@type").asText())) continue;
                String company = data.path("hiringOrganization").path("name").asText("").trim();
                String id = data.path("identifier").path("value").asText("");
                if (company.isBlank() || company.length() > 150 || !id.matches("[0-9a-fA-F-]{36}")) continue;
                for (String url : urls) {
                    if (!url.endsWith("/" + id)) continue;
                    String board = URI.create(url).getPath().split("/")[1];
                    return Optional.of(new Candidate(board, company, url));
                }
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }

    static boolean koreanLocation(String location) {
        if (location == null || location.isBlank()) return false;
        String value = location.toLowerCase(Locale.ROOT);
        return value.trim().matches("(seoul|seoul hq|seoul, south korea|south korea|korea|pangyo|busan|daejeon|서울|서울특별시|경기|판교|부산|대전|대한민국|한국)");
    }

    private static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", AGENT).GET().build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) return null;
            byte[] bytes = body.readNBytes(2_000_001);
            return bytes.length > 2_000_000 ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }
    record Candidate(String board, String company, String jobUrl) { }
}
