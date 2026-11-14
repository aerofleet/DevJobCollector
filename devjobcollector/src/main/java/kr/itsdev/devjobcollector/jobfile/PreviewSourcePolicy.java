package kr.itsdev.devjobcollector.jobfile;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Preliminary source policy. DNS results must be checked at connection time, not just before it. */
final class PreviewSourcePolicy {
    private static final Set<String> HOSTS = Set.of("alio.go.kr", "www.alio.go.kr", "opendata.alio.go.kr");
    private static final Pattern EXTENSION = Pattern.compile("(?i).*\\.(hwp|hwpx|pdf|png|jpe?g|webp)$");
    private static final Pattern LEGACY_QUERY = Pattern.compile("recrutAtchFileNo=([0-9]{1,20})");

    static URI source(String filename, String url) {
        if (filename == null || !EXTENSION.matcher(filename).matches() || url == null) throw new IllegalArgumentException("Unsupported document");
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid source", ex); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || !HOSTS.contains(host.toLowerCase(Locale.ROOT))
                || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null) throw new IllegalArgumentException("Source is not allowed");
        if (host.equalsIgnoreCase("opendata.alio.go.kr") && "/recruit/downloadAtchFile".equals(uri.getRawPath())) {
            var match = LEGACY_QUERY.matcher(uri.getRawQuery() == null ? "" : uri.getRawQuery());
            if (!match.matches()) throw new IllegalArgumentException("Invalid legacy source");
            return URI.create("https://www.alio.go.kr/download/download.json?fileNo=" + match.group(1));
        }
        return uri;
    }

    static boolean publicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        // Fail closed for IPv6 until transition and special-use ranges are fully evaluated.
        if (bytes.length == 16) return false;
        if (bytes.length != 4) return false;
        int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 169 && b == 254) && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && (b == 168 || b == 0 || (b == 88 && c == 99)))
                && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                && !(a == 203 && b == 0 && c == 113);
    }

    private PreviewSourcePolicy() {}
}
