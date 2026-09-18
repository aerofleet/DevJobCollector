package kr.itsdev.devjobcollector.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobSearchKeyword;

final class JobSearchSnippet {

    static final int MAX_LENGTH = 180;
    private static final int LEADING_CONTEXT = 60;

    private JobSearchSnippet() {
    }

    static String from(JobPost jobPost, String keyword) {
        List<String> searchTerms = JobSearchKeyword.expandedTokens(keyword);
        if (searchTerms.isEmpty()) {
            return null;
        }

        String snippet = fromText(jobPost.getApplyQual(), searchTerms);
        return snippet != null ? snippet : fromText(jobPost.getProcessInfo(), searchTerms);
    }

    private static String fromText(String source, List<String> searchTerms) {
        if (source == null || source.isBlank()) {
            return null;
        }

        String displayText = Normalizer.normalize(source, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim();
        String searchableText = displayText.toLowerCase(Locale.ROOT);
        int matchIndex = searchTerms.stream()
                .mapToInt(searchableText::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(-1);
        if (matchIndex < 0) {
            return null;
        }

        int start = Math.max(0, matchIndex - LEADING_CONTEXT);
        int end = Math.min(displayText.length(), start + MAX_LENGTH);
        if (end - start < MAX_LENGTH) {
            start = Math.max(0, end - MAX_LENGTH);
        }

        String prefix = start > 0 ? "…" : "";
        String suffix = end < displayText.length() ? "…" : "";
        return prefix + displayText.substring(start, end).trim() + suffix;
    }
}
