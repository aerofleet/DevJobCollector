package kr.itsdev.devjobcollector.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Conservative local redaction. Regex coverage is NOT proof of de-identification.
 * Free text remains blocked for external use until an independently evaluated detector exists.
 */
@Component
public class ResumePiiGate {
    public static final String VERSION = "local-redaction-v1";
    private static final String MASK = "[익명]";
    private static final Map<String, Pattern> RULES = new LinkedHashMap<>();
    static {
        RULES.put("EMAIL", Pattern.compile("[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+\\.[\\p{L}]{2,}"));
        RULES.put("RESIDENT_ID", Pattern.compile("(?<!\\d)\\d{6}[\\s-]*[1-8]\\d{6}(?!\\d)"));
        RULES.put("PHONE", Pattern.compile("(?<!\\d)(?:(?:\\+82[\\s.-]*(?:\\(0\\)[\\s.-]*)?)|0)(?:1[016789]|2|[3-6][1-5]|70)[\\s.-]*\\d{3,4}[\\s.-]*\\d{4}(?!\\d)"));
        RULES.put("URL", Pattern.compile("(?i)(?:https?://|www\\.)[^\\s<>\"']+"));
        RULES.put("IDENTITY_LINE", Pattern.compile("(?im)^(?:이름|성명|name|주소|address|생년월일|birth\\s*date)\\s*[:：]\\s*[^\\r\\n]+"));
        RULES.put("ADDRESS", Pattern.compile("(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|전라|경상|제주)[^\\r\\n]{0,20}(?:시|도|구|군)[^\\r\\n]{0,100}(?:로|길|동|읍|면)\\s*\\d[^\\r\\n]*"));
    }

    public Result mask(String input, List<String> knownPersonalValues) {
        if (input == null || input.isBlank() || input.length() > 60_000) {
            throw ResumeTextExtractor.failure(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_RESUME_TEXT");
        }
        String text = normalize(input);
        Map<String, Integer> counts = new LinkedHashMap<>();
        // Longest first prevents a shorter personal value from leaving part of an address/name behind.
        var values = knownPersonalValues.stream().filter(java.util.Objects::nonNull)
                .map(ResumePiiGate::normalize).map(String::strip).filter(value -> value.length() >= 2)
                .distinct().sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList();
        for (String value : values) {
            var matcher = Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            int count = (int) matcher.results().count();
            if (count > 0) {
                counts.merge("KNOWN_PERSONAL_VALUE", count, Integer::sum);
                text = matcher.replaceAll(MASK);
            }
        }
        for (var rule : RULES.entrySet()) {
            var matcher = rule.getValue().matcher(text);
            int count = (int) matcher.results().count();
            if (count > 0) {
                counts.merge(rule.getKey(), count, Integer::sum);
                text = matcher.replaceAll(MASK);
            }
        }
        return new Result(text, false, "PII_COVERAGE_UNVERIFIED", VERSION,
                Map.copyOf(counts), hash(text));
    }

    public void requireExternalTransmission(Result result) {
        // No config switch may silently promote this heuristic detector to an approved PII gate.
        throw ResumeTextExtractor.failure(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "BLOCKED_PII");
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[\\p{Cf}]", "");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public record Result(String maskedText, boolean piiGatePassed, String blockReason, String piiMaskVersion,
                         Map<String, Integer> piiCategorySummary, String maskedContentHash) {
        @Override public String toString() {
            return "PiiResult[piiGatePassed=" + piiGatePassed + ", version=" + piiMaskVersion + "]";
        }
    }
}
