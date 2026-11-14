package kr.itsdev.devjobcollector.collection.adapter;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import org.junit.jupiter.api.Test;

class RecruitBoardDetailParserTest {
    private final RecruitBoardDetailParser parser = new RecruitBoardDetailParser();
    private final URI detail = URI.create("https://example.com/bbs/board.php?bo_table=recruit&wr_id=77");

    @Test void extractsOnlyImagesInTheBodyAndDeduplicates() {
        String html = "<img src='/logo.jpg'><div class='board-content'><img src='/data/job.jpg'>"
                + "<img src='/data/job.jpg'><img src='https://example.com/data/job2.png'></div>";
        assertThat(parser.images(html, detail)).containsExactly("https://example.com/data/job.jpg", "https://example.com/data/job2.png");
    }

    @Test void rejectsUntrustedOrUnsupportedSourcesAndUnknownPages() {
        for (String url : new String[] {"http://example.com/a.jpg", "https://evil.example/a.jpg",
                "https://example.com.evil.example/a.jpg", "https://example.com@evil.example/a.jpg",
                "https://example.com/a.svg", "https://127.0.0.1/a.png"}) {
            assertThatThrownBy(() -> parser.images("<div class='board-content'><img src='" + url + "'></div>", detail))
                    .as(url).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> parser.images("<p>no body</p>", detail)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void evaluatesThirtyLabeledImageCases() {
        for (int i = 0; i < 10; i++) {
            String valid = "https://example.com/data/job-" + i + ".jpg";
            assertThat(parser.images("<div class='board-content'><img src='" + valid + "'></div>", detail))
                    .containsExactly(valid);
            String offsite = "https://outside" + i + ".example/file.png";
            assertThatThrownBy(() -> parser.images("<div class='board-content'><img src='" + offsite + "'></div>", detail))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(parser.images("<img src='/logo" + i + ".png'><div class='board-content'>내용</div>", detail))
                    .isEmpty();
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "DJC_RECRUIT_DETAIL_FIXTURE", matches = ".+")
    void extractsThePublicWellfineFixtureWithoutSavingItsHtmlInTheRepository() throws Exception {
        String html = java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("DJC_RECRUIT_DETAIL_FIXTURE")));
        var url = URI.create("https://www.wellfine.co.kr/bbs/board.php?bo_table=recruit&wr_id=77");
        assertThat(parser.images(html, url)).hasSize(1)
                .allMatch(image -> image.startsWith("https://www.wellfine.co.kr/") && image.endsWith(".jpg"));
    }
}
