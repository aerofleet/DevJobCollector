package kr.itsdev.devjobcollector.service;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/** Fixed public origin, no redirects, bounded response and timeouts. */
@Component
public class StripeSalaryPageClient {
    private static final int MAX_BYTES = 3_000_000;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public Optional<String> fetch(String sourceJobId) {
        if (sourceJobId == null || !sourceJobId.matches("[0-9]{1,20}")) return Optional.empty();
        var uri = URI.create("https://stripe.com/jobs/search?gh_jid=" + sourceJobId);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "DevJobCollector/1.0")
                .header("Accept", "text/html").GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type")
                        .orElse("").toLowerCase(java.util.Locale.ROOT).startsWith("text/html")) return Optional.empty();
                byte[] data = body.readNBytes(MAX_BYTES + 1);
                if (data.length > MAX_BYTES) return Optional.empty();
                return Optional.of(new String(data, StandardCharsets.UTF_8));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
