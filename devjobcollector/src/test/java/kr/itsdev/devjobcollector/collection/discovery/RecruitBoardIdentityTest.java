package kr.itsdev.devjobcollector.collection.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecruitBoardIdentityTest {
    @Test
    void identifiesOnlyCanonicalBoardListUrls() {
        String url = "https://example.com/bbs/board.php?bo_table=recruit";
        assertThat(RecruitBoardIdentity.identify(url)).isPresent()
                .contains(RecruitBoardIdentity.identify("https://EXAMPLE.com:443/bbs/board.php?bo_table=recruit").orElseThrow());
        assertThat(RecruitBoardIdentity.identify(url + "&wr_id=1")).isEmpty();
        assertThat(RecruitBoardIdentity.identify(url + "#fragment")).isEmpty();
        assertThat(RecruitBoardIdentity.identify("http://example.com/bbs/board.php?bo_table=recruit")).isEmpty();
        assertThat(RecruitBoardIdentity.identify("https://example.com@evil.example/bbs/board.php?bo_table=recruit")).isEmpty();
    }
}
