package kr.itsdev.devjobcollector.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative classification: ambiguous and preferred-only evidence is not a required career. */
public record ExperienceClassification(String category, String label) {
    private static final Pattern YEAR = Pattern.compile("(?i)(?:경력|실무)[^\\n.!?]{0,25}?(\\d{1,2})\\s*년(?!\\s*(?:이하|미만|차))|(?:\\d{1,2})\\s*년\\s*이상의?[^\\n.!?]{0,20}?(?:경력|실무|개발|업무)|(?:at least|minimum of|minimum)\\s+(\\d{1,2})\\s+years?[^\\n.!?]{0,30}experience|(?<!\\d)(\\d{1,2})\\+\\s+years?[^\\n.!?]{0,30}experience");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");
    private static final Pattern YEAR_RANGE = Pattern.compile("\\d{1,2}\\s*(?:년\\s*)?[~～–-]\\s*\\d{1,2}\\s*년");
    private static final Pattern PREFERRED = Pattern.compile("(?i)우대\\s*(?:사항|요건|조건)|preferred\\s+(?:qualifications|requirements)|nice to have");
    private static final Pattern REQUIRED = Pattern.compile("(?i)자격\\s*요건|지원\\s*자격|필수\\s*(?:요건|사항|조건)|(?:minimum\\s+)?requirements|qualifications");
    private static final Pattern OTHER = Pattern.compile("(?i)주요\\s*업무|담당\\s*업무|업무\\s*내용|복리\\s*후생|근무\\s*조건|전형\\s*절차|접수\\s*방법|responsibilities|benefits|about the (?:role|team)");

    public static ExperienceClassification classify(String source, String title, String description, boolean direct) {
        String stated = source == null ? "" : source.trim();
        String text = description == null ? "" : description.replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])>", "\n")
                .replaceAll("<[^>]*>", " ").replace("\u200b", "")
                .replaceAll("\\s*◆\\s*", "\n").replaceAll("[ \\t]+-[ \\t]+", "\n- ");
        int minimum = 0;
        boolean minimumExplicit = false;
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
                        minimumExplicit = value.contains("이상") || value.contains("+")
                                || value.toLowerCase(Locale.ROOT).contains("minimum")
                                || value.toLowerCase(Locale.ROOT).contains("at least");
                    }
                }
            }
        }
        if (minimum > 0) return new ExperienceClassification("EXPERIENCED", "경력 " + minimum + "년" + (minimumExplicit ? " 이상" : ""));
        if (!stated.contains("무관") && stated.matches(".*\\d+\\s*년.*")) return new ExperienceClassification("EXPERIENCED", stated);
        if (stated.contains("신입") && stated.contains("경력")) return new ExperienceClassification("BOTH", stated);
        if (stated.contains("신입")) return new ExperienceClassification("ENTRY", stated);
        if (stated.contains("경력") && !stated.contains("무관") && !stated.contains("확인")) return new ExperienceClassification("EXPERIENCED", stated);
        if ((direct && stated.contains("무관")) || text.matches("(?s).*경력\\s*무관.*")) return new ExperienceClassification("ANY", "경력무관");
        return new ExperienceClassification("UNKNOWN", "경력 확인 필요");
    }
}
