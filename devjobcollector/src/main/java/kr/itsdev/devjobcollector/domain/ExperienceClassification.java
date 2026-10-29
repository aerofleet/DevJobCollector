package kr.itsdev.devjobcollector.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/** Conservative classification: ambiguous and preferred-only evidence is not a required career. */
public record ExperienceClassification(String category, String label, String evidence, String method) {
    private static final Pattern YEAR = Pattern.compile("(?i)(?:경력|실무)[^\\n.!?]{0,25}?(\\d{1,2})\\s*년(?!\\s*(?:이하|미만|차))|(?:\\d{1,2})\\s*년\\s*이상의?[^\\n.!?]{0,120}?(?:경력|실무)|(?:at least|minimum of|minimum)\\s+(\\d{1,2})\\s+years?[^\\n.!?]{0,100}experience|(?<!\\d)(\\d{1,2})\\+\\s+years?[^\\n.!?]{0,100}experience");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");
    private static final Pattern YEAR_RANGE = Pattern.compile("\\d{1,2}\\s*(?:년\\s*)?[~～–-]\\s*\\d{1,2}\\s*년");
    private static final Pattern PREFERRED = Pattern.compile("(?i)우대\\s*(?:사항|요건|조건)|preferred\\s+(?:qualifications|requirements)|nice to have");
    private static final Pattern REQUIRED = Pattern.compile("(?i)자격\\s*(?:요건|조건)|지원\\s*자격|필수\\s*(?:요건|사항|조건)|(?:minimum\\s+)?requirements|qualifications");
    private static final Pattern OTHER = Pattern.compile("(?i)주요\\s*업무|담당\\s*업무|업무\\s*내용|복리\\s*후생|근무\\s*조건|전형\\s*절차|접수\\s*방법|responsibilities|benefits|about the (?:role|team)");

    public static ExperienceClassification classify(String source, String title, String description, boolean direct) {
        String stated = source == null ? "" : source.trim();
        if (!stated.contains("무관") && stated.matches(".*\\d+\\s*(?:년|개월).*")) {
            return new ExperienceClassification("EXPERIENCED", stated, stated, "SOURCE");
        }
        String text = description == null ? "" : HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(description))
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])>", "\n")
                .replaceAll("<[^>]*>", "").replace('\u00a0', ' ').replace("\u200b", "")
                .replaceAll("\\s*◆\\s*", "\n").replaceAll("[ \\t]+-[ \\t]+", "\n- ");
        int minimum = 0;
        boolean minimumExplicit = false;
        String evidence = null;
        boolean inRequired = false;
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (PREFERRED.matcher(value).find()) { inRequired = false; continue; }
            if (REQUIRED.matcher(value).find()) { inRequired = true; }
            if (OTHER.matcher(value).find()) { inRequired = false; continue; }
            if (!inRequired || value.contains("우대") || value.toLowerCase(Locale.ROOT).contains("preferred")
                    || YEAR_RANGE.matcher(value).find()) continue;
            Matcher match = YEAR.matcher(value);
            while (match.find()) {
                Matcher number = NUMBER.matcher(match.group());
                if (number.find()) {
                    int years = Integer.parseInt(number.group());
                    if (years > minimum) {
                        minimum = years;
                        evidence = value.length() > 500 ? value.substring(0, 500) : value;
                        minimumExplicit = value.contains("이상") || value.contains("+")
                                || value.toLowerCase(Locale.ROOT).contains("minimum")
                                || value.toLowerCase(Locale.ROOT).contains("at least");
                    }
                }
            }
        }
        if (minimum > 0) return new ExperienceClassification("EXPERIENCED", "경력 " + minimum + "년" + (minimumExplicit ? " 이상" : ""), evidence, "RULE");
        if (stated.contains("신입") && stated.contains("경력")) return new ExperienceClassification("BOTH", stated, stated, "SOURCE");
        if (stated.contains("신입")) return new ExperienceClassification("ENTRY", stated, stated, "SOURCE");
        if (stated.contains("경력") && !stated.contains("무관") && !stated.contains("확인")) return new ExperienceClassification("EXPERIENCED", stated, stated, "SOURCE");
        if (direct && stated.contains("무관")) return new ExperienceClassification("ANY", "경력무관", stated, "SOURCE");
        Matcher unrestricted = Pattern.compile("경력\\s*무관").matcher(text);
        if (unrestricted.find()) return new ExperienceClassification("ANY", "경력무관", unrestricted.group(), "RULE");
        return new ExperienceClassification("UNKNOWN", "경력 확인 필요", null, "UNKNOWN");
    }
}
