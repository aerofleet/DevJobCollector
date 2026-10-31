package kr.itsdev.devjobcollector.collection.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Work24ResponseParserTest {
    private final Work24ResponseParser parser = new Work24ResponseParser();

    @Test
    void parsesDocumentedListAndKeepsOpenEndedDeadlineUninterpreted() {
        var page = parser.parse("""
                <wantedRoot><total>1</total><startPage>1</startPage><display>10</display>
                <wanted><wantedAuthNo>K123</wantedAuthNo><company>테스트 기업</company>
                <title>Java 개발자</title><region>서울</region><career>경력무관</career>
                <regDt>20261031</regDt><closeDt>채용시까지</closeDt>
                <wantedInfoUrl>https://www.work24.go.kr/job/1</wantedInfoUrl><jobsCd>133201</jobsCd>
                </wanted></wantedRoot>
                """);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.jobs()).singleElement().satisfies(job -> {
            assertThat(job.id()).isEqualTo("K123");
            assertThat(job.title()).isEqualTo("Java 개발자");
            assertThat(job.closingDate()).isEqualTo("채용시까지");
        });
    }

    @Test
    void acceptsGenuineEmptyPageButRejectsErrorsAndIncompleteRecords() {
        assertThat(parser.parse("<wantedRoot><total>0</total><startPage>1</startPage></wantedRoot>").jobs()).isEmpty();
        assertThatThrownBy(() -> parser.parse("<error><message>invalid key</message></error>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("<wantedRoot><total>1</total><startPage>1</startPage><wanted><title>Developer</title></wanted></wantedRoot>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("<wantedRoot><total>0</total><startPage>0</startPage></wantedRoot>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("<wantedRoot><total>1</total><startPage>1</startPage></wantedRoot>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExternalEntityDocuments() {
        assertThatThrownBy(() -> parser.parse("<!DOCTYPE wantedRoot [<!ENTITY xxe SYSTEM 'file:///secret'>]><wantedRoot><total>0</total><startPage>1</startPage></wantedRoot>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
