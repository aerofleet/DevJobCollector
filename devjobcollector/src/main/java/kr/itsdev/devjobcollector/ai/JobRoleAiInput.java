package kr.itsdev.devjobcollector.ai;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.web.util.HtmlUtils;

/** Input for a proposal. Long or missing source text is declined rather than silently truncated. */
public final class JobRoleAiInput {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private JobRoleAiInput() {}

    public static Prepared prepare(long jobId, String source, String title, String rawContent, String catalogVersion, String model) {
        if (jobId <= 0 || source == null || source.isBlank() || title == null || title.isBlank() || title.length() > 255 || rawContent == null
                || catalogVersion == null || catalogVersion.isBlank() || !"gpt-5.4-mini".equals(model))
            throw new IllegalArgumentException("INVALID_ROLE_INPUT");
        String normalizedTitle = title.trim();
        var document = Jsoup.parse(HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(rawContent)));
        String content = document.text().replace('\u00a0', ' ').trim();
        if (content.length() > 4000) content = extractRelevantSections(document.wholeText());
        if (content.isBlank() || content.length() > 4000) throw new IllegalArgumentException("ROLE_CONTENT_UNUSABLE");
        Map<String, String> hashed = new TreeMap<>();
        hashed.put("jobId", Long.toString(jobId));
        hashed.put("source", source);
        hashed.put("title", normalizedTitle);
        hashed.put("content", content);
        hashed.put("rulesVersion", JobRoleAiRules.VERSION);
        hashed.put("catalogVersion", catalogVersion);
        hashed.put("catalog", String.join("\n", JobRoleAiRules.EXISTING_ROLES.stream().sorted().toList()));
        hashed.put("model", model);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(MAPPER.writeValueAsBytes(hashed)));
            return new Prepared(jobId, source, normalizedTitle, content, catalogVersion, model, hash);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("ROLE_INPUT_HASH_FAILED", e);
        }
    }

    private static String extractRelevantSections(String text) {
        StringBuilder selected = new StringBuilder();
        int section = 0;
        boolean foundDuties = false;
        boolean foundRequirements = false;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replace('\u00a0', ' ').trim();
            String heading = line.replaceAll("^[#\\[\\] ]+|[#\\[\\]:： ]+$", "").toLowerCase(Locale.ROOT);
            if (heading.matches("(?:job description|responsibilities|what you.ll do|주요 업무|업무 내용|담당 업무)")) {
                section = 1;
                continue;
            } else if (heading.matches("(?:basic qualifications|minimum qualifications|required qualifications|자격 요건|필수 요건)")) {
                section = 2;
                continue;
            } else if (heading.matches("(?:preferred qualifications|benefits|compensation|about us|우대 사항|복리후생|회사 소개)")) {
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

    public record Prepared(long jobId, String source, String title, String content, String catalogVersion, String model, String inputHash) {
        public String evidenceSource() { return title + "\n" + content; }
    }
}
