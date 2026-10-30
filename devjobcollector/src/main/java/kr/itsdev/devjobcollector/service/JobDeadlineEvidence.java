package kr.itsdev.devjobcollector.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display-only evidence. Date-only values must not change expiration decisions. */
public final class JobDeadlineEvidence {
    private static final LocalDate UNKNOWN = LocalDate.of(9999, 12, 31);
    private static final Pattern CANDIDATE = Pattern.compile(
            "(?iu)(?:applications?\\s+(?:close|closes|closing)|application\\s+deadline|closing\\s+date|접수\\s*마감|지원\\s*마감|마감일)\\s*[:：-]?\\s*(\\d{1,2}(?:st|nd|rd|th)?\\s+[a-z]+\\s+\\d{4}|\\d{4}[-./년]\\s*\\d{1,2}[-./월]\\s*\\d{1,2}일?)");
    private static final Pattern ENGLISH = Pattern.compile("(?i)(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]+)\\s+(\\d{4})");
    private static final Pattern NUMERIC = Pattern.compile("(\\d{4})[-./년]\\s*(\\d{1,2})[-./월]\\s*(\\d{1,2})일?");

    public record Candidate(LocalDate date, String evidence) { }
    public record Result(String status, LocalDate localDate, String evidence, List<Candidate> candidates) { }

    private JobDeadlineEvidence() { }

    public static Result resolve(LocalDate structured, String content) {
        List<Candidate> candidates = new ArrayList<>();
        boolean invalid = false;
        if (structured != null && !UNKNOWN.equals(structured)) candidates.add(new Candidate(structured, "endDate"));
        Matcher matcher = CANDIDATE.matcher(content == null ? "" : content.replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])>", "\n").replaceAll("<[^>]+>", ""));
        while (matcher.find()) {
            LocalDate date = parse(matcher.group(1));
            if (date == null) invalid = true;
            else candidates.add(new Candidate(date, matcher.group().trim()));
        }
        if (candidates.stream().map(Candidate::date).distinct().count() > 1)
            return new Result("CONFLICT", null, null, List.copyOf(candidates));
        if (invalid) return new Result("AMBIGUOUS", null, null, List.copyOf(candidates));
        if (candidates.isEmpty()) return new Result("UNAVAILABLE", null, null, List.of());
        Candidate first = candidates.getFirst();
        return new Result("PRESENT", first.date(), first.evidence(), List.copyOf(candidates));
    }

    private static LocalDate parse(String value) {
        try {
            Matcher english = ENGLISH.matcher(value);
            if (english.matches()) {
                Month month = Month.valueOf(english.group(2).toUpperCase(Locale.ROOT));
                return LocalDate.of(Integer.parseInt(english.group(3)), month, Integer.parseInt(english.group(1)));
            }
            Matcher numeric = NUMERIC.matcher(value);
            if (numeric.matches()) return LocalDate.of(Integer.parseInt(numeric.group(1)), Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(3)));
        } catch (DateTimeException | IllegalArgumentException ignored) { }
        return null;
    }
}
