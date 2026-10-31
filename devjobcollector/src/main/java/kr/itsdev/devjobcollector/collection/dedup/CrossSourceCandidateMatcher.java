package kr.itsdev.devjobcollector.collection.dedup;

import java.text.Normalizer;
import java.util.Locale;

/** Produces review candidates only. It never resolves companies or merges jobs. */
public final class CrossSourceCandidateMatcher {
    public record Posting(Long verifiedCompanyId, String source, String sourceJobId, String title) {}
    public enum Decision {
        REVIEW_EXACT_TITLE,
        REVIEW_SAME_COMPANY,
        UNRESOLVED_COMPANY,
        DIFFERENT_COMPANY,
        SAME_SOURCE,
        INVALID_INPUT
    }

    public Decision compare(Posting left, Posting right) {
        if (!valid(left) || !valid(right)) return Decision.INVALID_INPUT;
        if (left.source().equalsIgnoreCase(right.source())) return Decision.SAME_SOURCE;
        if (left.verifiedCompanyId() == null || right.verifiedCompanyId() == null) return Decision.UNRESOLVED_COMPANY;
        if (!left.verifiedCompanyId().equals(right.verifiedCompanyId())) return Decision.DIFFERENT_COMPANY;
        String leftTitle = normalizeTitle(left.title());
        String rightTitle = normalizeTitle(right.title());
        if (!leftTitle.isEmpty() && leftTitle.equals(rightTitle)) return Decision.REVIEW_EXACT_TITLE;
        return Decision.REVIEW_SAME_COMPANY;
    }

    private static boolean valid(Posting job) {
        return job != null && job.source() != null && !job.source().isBlank()
                && job.sourceJobId() != null && !job.sourceJobId().isBlank()
                && (job.verifiedCompanyId() == null || job.verifiedCompanyId() > 0);
    }

    private static String normalizeTitle(String title) {
        if (title == null) return "";
        return Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
}
