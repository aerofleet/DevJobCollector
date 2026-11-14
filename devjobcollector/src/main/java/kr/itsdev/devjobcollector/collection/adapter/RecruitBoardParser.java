package kr.itsdev.devjobcollector.collection.adapter;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.collection.support.ContentHash;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** Parses the explicitly marked recruit board, never arbitrary links on a company site. */
@Component
public class RecruitBoardParser {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final Pattern DATES = Pattern.compile("(\\d{4}\\.\\d{2}\\.\\d{2})\\s*~\\s*(\\d{4}\\.\\d{2}\\.\\d{2})");

    public ParseResult parse(String html, URI board, String company) {
        var document = Jsoup.parse(html);
        var rows = document.select("tr:has(.badge.recruit-now), tr:has(.badge.recruit-end)");
        if (rows.isEmpty() || queryValue(board, "bo_table") == null) return new ParseResult(List.of(), java.util.Set.of(), false);
        List<JobRawDto> jobs = new ArrayList<>();
        var ids = new java.util.HashSet<String>();
        var closedIds = new java.util.LinkedHashSet<String>();
        boolean valid = document.select("tr:has(a[href*=wr_id=]):not(:has(.badge.recruit-now)):not(:has(.badge.recruit-end))").isEmpty();
        for (var row : rows) {
            var active = row.select(".badge.recruit-now");
            var closed = row.select(".badge.recruit-end");
            var links = row.select("a[href*=wr_id=]");
            if (active.size() + closed.size() != 1 || links.size() != 1) { valid = false; continue; }
            if ((!active.isEmpty() && !active.first().text().trim().equals("접수중"))
                    || (!closed.isEmpty() && !closed.first().text().trim().equals("채용마감"))) {
                valid = false; continue;
            }
            var link = links.first();
            URI url;
            try { url = board.resolve(link.attr("href")); }
            catch (IllegalArgumentException ex) { valid = false; continue; }
            String id = queryValue(url, "wr_id");
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                    || !url.getHost().equalsIgnoreCase(board.getHost()) || url.getUserInfo() != null
                    || (url.getPort() != -1 && url.getPort() != 443)
                    || !board.getPath().equals(url.getPath()) || id == null || !id.matches("[0-9]{1,18}")
                    || !java.util.Objects.equals(queryValue(board, "bo_table"), queryValue(url, "bo_table"))) {
                valid = false; continue;
            }
            if (!ids.add(id)) { valid = false; continue; }
            if (!closed.isEmpty()) { closedIds.add(id); continue; }
            var dates = DATES.matcher(row.text());
            String title = link.text().trim();
            if (title.isEmpty() || !dates.find()) { valid = false; continue; }
            try {
                LocalDate start = LocalDate.parse(dates.group(1), DATE);
                LocalDate end = LocalDate.parse(dates.group(2), DATE);
                if (end.isBefore(start)) { valid = false; continue; }
                String payload = title + "\n" + url + "\n" + start + "\n" + end;
                jobs.add(new JobRawDto(SourceType.COMPANY_PAGE, id, company, title, null, null, "상세 내용 확인", null,
                        url.toString(), url.toString(), "상세 내용은 원문에서 확인해 주세요.",
                        start.atStartOfDay(SEOUL).toInstant(), null,
                        end.plusDays(1).atStartOfDay(SEOUL).minusNanos(1).toInstant(),
                        ContentHash.sha256(payload), payload));
            } catch (DateTimeParseException ex) { valid = false; }
        }
        return new ParseResult(List.copyOf(jobs), java.util.Set.copyOf(closedIds), valid);
    }

    private static String queryValue(URI uri, String name) {
        if (uri.getRawQuery() == null) return null;
        String result = null;
        for (String part : uri.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                if (result != null) return null;
                result = pair[1];
            }
        }
        return result;
    }

    public record ParseResult(List<JobRawDto> jobs, java.util.Set<String> closedIds, boolean valid) { }
}
