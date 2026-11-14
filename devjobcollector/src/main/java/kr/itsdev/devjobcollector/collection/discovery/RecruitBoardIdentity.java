package kr.itsdev.devjobcollector.collection.discovery;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import kr.itsdev.devjobcollector.collection.support.ContentHash;

/** Identity for a canonical HTTPS GnuBoard recruitment-list URL, not an ownership proof. */
public final class RecruitBoardIdentity {
    private RecruitBoardIdentity() { }

    public static Optional<String> identify(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !"/bbs/board.php".equals(uri.getRawPath()) || uri.getRawQuery() == null
                    || !uri.getRawQuery().matches("bo_table=[A-Za-z0-9_]{1,40}")) return Optional.empty();
            String canonical = "https://" + uri.getHost().toLowerCase(Locale.ROOT) + uri.getRawPath()
                    + "?" + uri.getRawQuery();
            return Optional.of("recruit-board-" + ContentHash.sha256(canonical));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }
}
