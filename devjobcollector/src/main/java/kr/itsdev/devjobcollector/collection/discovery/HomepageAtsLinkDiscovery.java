package kr.itsdev.devjobcollector.collection.discovery;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extracts suggestions from already obtained HTML; it never fetches URLs or creates targets. */
@Service
public class HomepageAtsLinkDiscovery {
    private final AtsFingerprintService fingerprints;

    public HomepageAtsLinkDiscovery(AtsFingerprintService fingerprints) {
        this.fingerprints = fingerprints;
    }

    public List<Candidate> discover(String homepageUrl, String html) {
        if (homepageUrl == null || html == null) return List.of();
        try {
            URI homepage = URI.create(homepageUrl);
            if (!"https".equalsIgnoreCase(homepage.getScheme()) || homepage.getHost() == null
                    || homepage.getUserInfo() != null) return List.of();
            Map<AtsFingerprintService.Fingerprint, Candidate> results = new LinkedHashMap<>();
            // A supplied homepage can itself be the supported ATS board.
            fingerprints.identify(homepage.toString()).ifPresent(fingerprint ->
                    results.put(fingerprint, new Candidate(fingerprint, homepage.toString())));
            // Resolve links against the supplied origin, never an untrusted HTML base element.
            Jsoup.parse(html).select("a[href]").forEach(link -> {
                try {
                    URI resolved = homepage.resolve(link.attr("href").trim());
                    fingerprints.identify(resolved.toString()).ifPresent(fingerprint ->
                            results.putIfAbsent(fingerprint, new Candidate(fingerprint, resolved.toString())));
                } catch (IllegalArgumentException ignored) {
                    // Malformed links cannot become candidates.
                }
            });
            return List.copyOf(results.values());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    public record Candidate(AtsFingerprintService.Fingerprint fingerprint, String sourceUrl) { }
}
