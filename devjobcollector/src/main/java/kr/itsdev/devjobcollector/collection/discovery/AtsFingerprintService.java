package kr.itsdev.devjobcollector.collection.discovery;

import kr.itsdev.devjobcollector.collection.domain.SourceType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Identifies supported hosted boards; a match alone does not verify ownership or authorize collection. */
@Service
public class AtsFingerprintService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,99}");
    private static final Pattern GREETING_IDENTIFIER = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    public Optional<Fingerprint> identify(String url) {
        if (url == null) return Optional.empty();
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return Optional.empty();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String suffix = ".career.greetinghr.com";
            if (host.endsWith(suffix)) {
                String identifier = host.substring(0, host.length() - suffix.length());
                return GREETING_IDENTIFIER.matcher(identifier).matches()
                        ? Optional.of(new Fingerprint(SourceType.GREETING, identifier)) : Optional.empty();
            }
            SourceType type = switch (host) {
                case "jobs.lever.co" -> SourceType.LEVER;
                case "job-boards.greenhouse.io", "boards.greenhouse.io" -> SourceType.GREENHOUSE;
                case "jobs.ashbyhq.com" -> SourceType.ASHBY;
                default -> null;
            };
            if (type == null || uri.getRawPath() == null) return Optional.empty();
            String[] segments = uri.getRawPath().split("/");
            return segments.length > 1 ? match(type, segments[1]) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Fingerprint> match(SourceType type, String identifier) {
        return IDENTIFIER.matcher(identifier).matches()
                ? Optional.of(new Fingerprint(type, identifier)) : Optional.empty();
    }

    public String greetingBoardUrl(String sourceUrl, String identifier) {
        URI uri = URI.create(sourceUrl);
        String path = "/ko/apply".equals(uri.getRawPath()) ? "/ko/apply" : "/ko/home";
        return "https://" + identifier + ".career.greetinghr.com" + path;
    }

    public record Fingerprint(SourceType provider, String sourceIdentifier) { }
}
