package kr.itsdev.devjobcollector.ai;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.web.util.HtmlUtils;

/** Input for a proposal. Long or missing source text is declined rather than silently truncated. */
public final class JobRoleAiInput {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private JobRoleAiInput() {}

    public static Prepared prepare(long jobId, String source, String title, String category, String rawContent, String catalogVersion, String model) {
        return prepare(jobId, source, title, category, rawContent, catalogVersion, model, JobRoleAiRules.EXISTING_ROLES);
    }

    public static Prepared prepare(long jobId, String source, String title, String category, String rawContent,
                                   String catalogVersion, String model, Set<String> allowedRoles) {
        return prepare(jobId, source, title, category, rawContent, catalogVersion, model, allowedRoles, false);
    }

    public static Prepared prepareCompact(long jobId, String source, String title, String category, String rawContent,
                                          String catalogVersion, String model, Set<String> allowedRoles) {
        return prepare(jobId, source, title, category, rawContent, catalogVersion, model, allowedRoles, true);
    }

    private static Prepared prepare(long jobId, String source, String title, String category, String rawContent,
                                    String catalogVersion, String model, Set<String> allowedRoles, boolean compact) {
        if (jobId <= 0 || source == null || source.isBlank() || title == null || title.isBlank() || title.length() > 255 || rawContent == null
                || (category != null && category.length() > 255) || catalogVersion == null || catalogVersion.isBlank() || !"gpt-5.4-mini".equals(model)
                || allowedRoles == null || allowedRoles.size() > 200 || !allowedRoles.containsAll(JobRoleAiRules.EXISTING_ROLES)
                || allowedRoles.stream().anyMatch(role -> !JobRoleAiRules.validRoleLabel(role)))
            throw new IllegalArgumentException("INVALID_ROLE_INPUT");
        String normalizedTitle = title.trim();
        var document = Jsoup.parse(HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(rawContent)));
        String content = document.text().replace('\u00a0', ' ').trim();
        if (compact || content.length() > 4000) {
            content = extractRelevantSections(document.wholeText(), 0);
            if (content.isEmpty()) content = extractRelevantSections(document.wholeText(), 1);
            if (content.isEmpty()) content = extractRelevantSections(document.wholeText(), 2);
        }
        if (content.isBlank() || content.length() > 4000) throw new IllegalArgumentException("ROLE_CONTENT_UNUSABLE");
        Map<String, String> hashed = new TreeMap<>();
        hashed.put("jobId", Long.toString(jobId));
        hashed.put("source", source);
        hashed.put("title", normalizedTitle);
        String normalizedCategory = category == null ? "" : category.trim();
        hashed.put("category", normalizedCategory);
        hashed.put("content", content);
        hashed.put("rulesVersion", JobRoleAiRules.VERSION);
        hashed.put("catalogVersion", catalogVersion);
        List<String> sortedRoles = allowedRoles.stream().sorted().toList();
        hashed.put("catalog", String.join("\n", sortedRoles));
        hashed.put("model", model);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(MAPPER.writeValueAsBytes(hashed)));
            return new Prepared(jobId, source, normalizedTitle, normalizedCategory, content, catalogVersion, model, List.copyOf(sortedRoles), hash);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("ROLE_INPUT_HASH_FAILED", e);
        }
    }

    public static Prepared prepareMatching(long jobId, String source, String title, String category, String rawContent,
                                           String catalogVersion, String model, Set<String> allowedRoles, String expectedHash) {
        Prepared original = prepare(jobId, source, title, category, rawContent, catalogVersion, model, allowedRoles);
        if (original.inputHash().equals(expectedHash)) return original;
        Prepared compact = prepareCompact(jobId, source, title, category, rawContent, catalogVersion, model, allowedRoles);
        if (compact.inputHash().equals(expectedHash)) return compact;
        throw new IllegalArgumentException("ROLE_INPUT_CHANGED");
    }

    private static String extractRelevantSections(String text, int version) {
        StringBuilder selected = new StringBuilder();
        int section = 0;
        boolean foundDuties = false;
        boolean foundRequirements = false;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replace('\u00a0', ' ').trim();
            String heading = line.replaceAll("^[#\\[\\] ]+|[#\\[\\]:： ]+$", "").toLowerCase(Locale.ROOT);
            if (heading.matches("(?:job description|responsibilities|what you.ll do|주요 업무|업무 내용|담당 업무)")
                    || (version >= 1 && heading.matches("what you will be doing"))
                    || (version >= 2 && heading.matches("key responsibilities"))) {
                section = 1;
                continue;
            } else if (heading.matches("(?:basic qualifications|minimum qualifications|required qualifications|자격 요건|필수 요건)")
                    || (version >= 1 && heading.matches("what you bring"))
                    || (version >= 2 && heading.matches("skill requirements & qualifications"))) {
                section = 2;
                continue;
            } else if (heading.matches("(?:preferred qualifications|benefits|compensation|about us|우대 사항|복리후생|회사 소개)")
                    || (version >= 1 && heading.matches("additional information(?:\\s*[-–—].*)?"))
                    || (version >= 2 && heading.matches("what makes cloudflare special\\?"))) {
                section = 0;
                continue;
            }
            if (section != 0 && !line.isEmpty()) {
                if (section == 1) foundDuties = true;
                if (section == 2) foundRequirements = true;
                selected.append(line).append(' ');
            }
        }
        if (!foundDuties || !foundRequirements || selected.length() > 4000) return "";
        return selected.toString().trim();
    }

    public record Prepared(long jobId, String source, String title, String category, String content, String catalogVersion, String model, List<String> allowedRoles, String inputHash) {
        public String evidenceSource() { return title + "\n" + category + "\n" + content; }
    }
}
