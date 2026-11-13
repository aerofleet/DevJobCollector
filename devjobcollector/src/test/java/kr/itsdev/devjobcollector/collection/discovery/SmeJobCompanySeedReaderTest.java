package kr.itsdev.devjobcollector.collection.discovery;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmeJobCompanySeedReaderTest {
    private static final String HEADER = "기업명,사업자번호,지역구분,종업원수,공고명,고용형태,공고등록일\r\n";
    private final SmeJobCompanySeedReader reader = new SmeJobCompanySeedReader();

    @Test
    void readsQuotedCsvAndRejectsInvalidIdentifiersWithoutEmittingRawNumbers() throws Exception {
        var seeds = new ArrayList<SmeJobCompanySeedReader.Seed>();
        var counts = reader.read(new StringReader("\ufeff" + HEADER
                + "\"테스트, 기업\",123-45-67890,서울,10,\"개발자, 백엔드\",정규직,2026-01-01\r\n"
                + "오류기업,123,서울,10,개발자,정규직,2026-01-01\r\n"), seeds::add);
        assertThat(counts).isEqualTo(new SmeJobCompanySeedReader.Counts(1, 1));
        assertThat(seeds).hasSize(1);
        assertThat(seeds.getFirst().companyName()).isEqualTo("테스트, 기업");
        assertThat(seeds.getFirst().jobTitle()).isEqualTo("개발자, 백엔드");
        assertThat(seeds.getFirst().businessNumberHash()).matches("[0-9a-f]{64}");
        assertThat(seeds.getFirst().toString()).doesNotContain("123-45-67890", "1234567890");
    }

    @Test
    void failsClosedForUnknownHeaders() {
        assertThatThrownBy(() -> reader.read(new StringReader("회사,번호\n테스트,123\n"), seed -> { }))
                .isInstanceOf(IOException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DJC_KOSME_FIXTURE", matches = ".+")
    void readsOfficialCp949FileWithoutEmittingRows() throws Exception {
        try (var input = Files.newBufferedReader(Path.of(System.getenv("DJC_KOSME_FIXTURE")), Charset.forName("MS949"))) {
            var counts = reader.read(input, seed -> { });
            System.out.println("KOSME_CSV_ACCEPTED_ROWS=" + counts.accepted() + " SKIPPED_ROWS=" + counts.skipped());
            assertThat(counts.accepted()).isPositive();
        }
    }
}
