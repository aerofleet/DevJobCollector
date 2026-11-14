package kr.itsdev.devjobcollector.collection.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecruitBoardParserTest {
    private final RecruitBoardParser parser = new RecruitBoardParser();
    private final URI board = URI.create("https://example.com/bbs/board.php?bo_table=recruit");

    @Test
    void acceptsOnlyActiveRowsInLabeledFixture() {
        StringBuilder html = new StringBuilder("<table>");
        IntStream.rangeClosed(1, 10).forEach(i -> html.append(row(i, "recruit-now", "접수중")));
        IntStream.rangeClosed(11, 20).forEach(i -> html.append(row(i, "recruit-end", "채용마감")));
        IntStream.rangeClosed(21, 30).forEach(i -> html.append("<tr><td><a href='/news/" + i + "'>소식</a></td></tr>"));
        html.append("</table>");
        var result = parser.parse(html.toString(), board, "회사");
        assertThat(result.valid()).isTrue();
        assertThat(result.jobs()).hasSize(10);
        assertThat(result.jobs()).extracting(job -> job.sourceJobId()).containsExactlyElementsOf(
                IntStream.rangeClosed(1, 10).mapToObj(Integer::toString).toList());
        assertThat(result.jobs().getFirst().plainDescription()).contains("원문");
    }

    @Test
    void failsClosedForCrossOriginAndMalformedDate() {
        String html = "<table>" + row(1, "recruit-now", "접수중").replace("/bbs/", "https://evil.example/bbs/")
                + row(2, "recruit-now", "접수중").replace("2026.12.01", "2026.99.01") + "</table>";
        var result = parser.parse(html, board, "회사");
        assertThat(result.valid()).isFalse();
        assertThat(result.jobs()).isEmpty();
    }

    @Test
    void missingBoardMarkerDoesNotMeanAnEmptySuccessfulCollection() {
        assertThat(parser.parse("<p>공지</p>", board, "회사").valid()).isFalse();
    }

    private static String row(int id, String status, String label) {
        return "<tr><td><a href='/bbs/board.php?bo_table=recruit&amp;wr_id=" + id
                + "'>개발자 " + id + "</a></td><td><span class='badge " + status + "'>" + label
                + "</span></td><td>2026.11.01~<br>2026.12.01</td></tr>";
    }
}
