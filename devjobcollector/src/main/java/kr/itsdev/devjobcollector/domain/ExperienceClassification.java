package kr.itsdev.devjobcollector.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/** Conservative classification: ambiguous and preferred-only evidence is not a required career. */
public record ExperienceClassification(String category, String label, String evidence, String method) {
    private static final Pattern YEAR = Pattern.compile("(?i)(?:경력|실무|경험)[^\\n.!?]{0,25}?(\\d{1,2})\\s*년(?!\\s*(?:이하|미만|차))|(?:\\d{1,2})\\s*년\\s*이상의?[^\\n.!?]{0,120}?(?:경력|실무|경험)|(?:at least|minimum of|minimum)\\s+(\\d{1,2})\\s+years?[^\\n.!?]{0,100}experience|(?<!\\d)(\\d{1,2})\\+\\s+years?[^\\n.!?]{0,100}experience");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");
    private static final Pattern YEAR_RANGE = Pattern.compile("\\d{1,2}\\s*(?:년\\s*)?[~～–-]\\s*\\d{1,2}\\s*년");
    private static final Pattern MINIMUM_ENGLISH_RANGE = Pattern.compile("(?i)\\b(?:minimum of|minimum|at least)\\s+(\\d{1,2})\\s*[-–~]\\s*(\\d{1,2})\\s*years?\\b[^\\n]{0,100}\\bexperience\\b");
    private static final Pattern PREFERRED = Pattern.compile("(?i)우대\\s*(?:사항|요건|조건)|preferred\\s*(?:[:：]|qualifications|requirements|skills)|nice to have|^(?:#{1,6}\\s*)?이런\\s*경험이\\s*있다면\\s*더\\s*좋아요\\s*[:：]?$|^while not required,?\\s*it['’]s an added plus if you also have\\s*[:：]?$");
    private static final Pattern REQUIRED = Pattern.compile("(?i)자격\\s*(?:요건|조건)|지원\\s*(?:자격|조건)|필수\\s*(?:요건|사항|조건)|^(?:#{1,6}\\s*)?이런\\s*분과\\s*함께하고\\s*싶어요\\s*[:：]?$|^(?:#{1,6}\\s*)?required\\s+skills(?:\\s+and\\s+experience)?\\s*[:：]?$|^we(?:['’]re| are) looking for someone who\\s*[:：]?$|(?:minimum\\s+)?requirements|qualifications|^we['’]d love to hear from you if you have\\s*[:：]?$|^your expertise\\s*[:：]?$");
    private static final Pattern OTHER = Pattern.compile("(?i)주요\\s*업무|담당\\s*업무|업무\\s*내용|복리\\s*후생|근무\\s*조건|전형\\s*절차|접수\\s*방법|responsibilities|benefits|about the (?:role|team)|^success measures\\s*[:：]?$");

    public static ExperienceClassification classify(String source, String title, String description, boolean direct) {
        String stated = source == null ? "" : source.trim();
        if (!stated.contains("무관") && stated.matches(".*\\d+\\s*(?:년|개월).*")) {
            return new ExperienceClassification("EXPERIENCED", stated, stated, "SOURCE");
        }
        String text = description == null ? "" : HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(description))
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])>", "\n")
                .replaceAll("<[^>]*>", "").replace('\u00a0', ' ').replace("\u200b", "")
                .replace("\ufeff", "").replaceAll("(?im)^(\\s*(?:required skills|preferred skills))\\s+(?=·\\s)", "$1\n")
                .replaceAll("[ \\t]+·(?=\\s)", "\n·").replaceAll("\\s*◆\\s*", "\n").replaceAll("[ \\t]+-[ \\t]+", "\n- ");
        int minimum = 0;
        boolean minimumExplicit = false;
        String minimumRange = null;
        String evidence = null;
        boolean inRequired = false;
        String unrestrictedEvidence = null;
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (PREFERRED.matcher(value).find()) { inRequired = false; continue; }
            boolean requiredHeading = REQUIRED.matcher(value).find();
            if (requiredHeading) { inRequired = true; }
            else if (value.matches("^#{1,6}\\s+.*")) { inRequired = false; continue; }
            if (value.matches("(?i)^(?:PID\\s*-\\s*[A-Z]?\\d+|pay transparency.*|pay range transparency.*|annual base salary.*|application limit.*|equal opportunity.*)$")) { inRequired = false; continue; }
            boolean bullet = value.matches("^[-*•●▪◦‣·]\\s*.*");
            if (!bullet && OTHER.matcher(value).find()) { inRequired = false; continue; }
            if (!inRequired) continue;
            Matcher explicitUnrestricted = Pattern.compile("경력\\s*구분\\s*[:：]\\s*무관").matcher(value);
            if (explicitUnrestricted.find()) unrestrictedEvidence = explicitUnrestricted.group();
            String candidate = value;
            String[] clauses = value.split(";", -1);
            if (clauses.length == 2 && clauses[1].trim().toLowerCase(Locale.ROOT).matches(".*\\bstrongly preferred\\.?$")
                    && !clauses[0].toLowerCase(Locale.ROOT).contains("preferred")) candidate = clauses[0].trim();
            if (candidate.matches(".*(?:학위|석사|박사)[^.!?\\n]{0,80}(?:또는|혹은)[^.!?\\n]{0,80}\\d{1,2}\\s*년.*") || candidate.contains("우대") || candidate.contains("선호") || candidate.toLowerCase(Locale.ROOT).contains("preferred")
                    || YEAR_RANGE.matcher(candidate).find()) continue;
            Matcher range = MINIMUM_ENGLISH_RANGE.matcher(candidate);
            if (range.find()) {
                int lower = Integer.parseInt(range.group(1));
                int upper = Integer.parseInt(range.group(2));
                if (lower > minimum && upper >= lower) {
                    minimum = lower;
                    minimumRange = lower + "~" + upper;
                    minimumExplicit = true;
                    evidence = candidate.length() > 500 ? candidate.substring(0, 500) : candidate;
                }
            }
            Matcher match = YEAR.matcher(candidate);
            while (match.find()) {
                Matcher number = NUMBER.matcher(match.group());
                if (number.find()) {
                    int years = Integer.parseInt(number.group());
                    if (years > minimum) {
                        minimum = years;
                        minimumRange = null;
                        evidence = candidate.length() > 500 ? candidate.substring(0, 500) : candidate;
                        minimumExplicit = candidate.contains("이상") || candidate.contains("+")
                                || candidate.toLowerCase(Locale.ROOT).contains("minimum")
                                || candidate.toLowerCase(Locale.ROOT).contains("at least");
                    }
                }
            }
        }
        if (unrestrictedEvidence != null && minimum == 0) return new ExperienceClassification("ANY", "경력무관", unrestrictedEvidence, "RULE");
        if (minimum > 0) return new ExperienceClassification("EXPERIENCED", minimumRange != null ? "경력 최소 " + minimumRange + "년" : "경력 " + minimum + "년" + (minimumExplicit ? " 이상" : ""), evidence, "RULE");
        if (stated.contains("신입") && stated.contains("경력")) return new ExperienceClassification("BOTH", stated, stated, "SOURCE");
        if (stated.contains("신입")) return new ExperienceClassification("ENTRY", stated, stated, "SOURCE");
        if (stated.contains("경력") && !stated.contains("무관") && !stated.contains("확인")) return new ExperienceClassification("EXPERIENCED", stated, stated, "SOURCE");
        if (direct && stated.contains("무관")) return new ExperienceClassification("ANY", "경력무관", stated, "SOURCE");
        Matcher unrestricted = Pattern.compile("경력\\s*무관").matcher(text);
        if (unrestricted.find()) return new ExperienceClassification("ANY", "경력무관", unrestricted.group(), "RULE");
        return new ExperienceClassification("UNKNOWN", "경력 확인 필요", null, "UNKNOWN");
    }
}
