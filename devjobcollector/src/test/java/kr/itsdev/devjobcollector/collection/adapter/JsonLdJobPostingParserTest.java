package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.CompanyPageCollectionProperties;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLdJobPostingParserTest {

    @Test
    void parsesSchemaOrgJobPostingFromGraph() throws IOException {
        JsonLdJobPostingParser parser = new JsonLdJobPostingParser(
                new ObjectMapper(), new CompanyPageCollectionProperties(null, 20_000));

        JsonLdJobPostingParser.ParseResult result = parser.parse(
                fixture(), URI.create("https://careers.example.com/jobs"), "기본회사");

        assertThat(result.jsonLdScriptCount()).isEqualTo(1);
        assertThat(result.malformedScriptCount()).isZero();
        assertThat(result.jobs()).singleElement().satisfies(this::assertMappedJob);
    }

    @Test
    void reportsMalformedJsonLdWithoutThrowing() {
        JsonLdJobPostingParser parser = new JsonLdJobPostingParser(
                new ObjectMapper(), new CompanyPageCollectionProperties(null, 20_000));

        JsonLdJobPostingParser.ParseResult result = parser.parse(
                "<script type='application/ld+json'>{broken}</script>",
                URI.create("https://careers.example.com/jobs"), "기본회사");

        assertThat(result.jobs()).isEmpty();
        assertThat(result.jsonLdScriptCount()).isEqualTo(1);
        assertThat(result.malformedScriptCount()).isEqualTo(1);
    }

    private void assertMappedJob(JobRawDto job) {
        assertThat(job.sourceJobId()).isEqualTo("backend-2026-01");
        assertThat(job.companyName()).isEqualTo("테스트회사");
        assertThat(job.title()).isEqualTo("백엔드 개발자");
        assertThat(job.location()).isEqualTo("서울 강남구 대한민국");
        assertThat(job.employmentType()).isEqualTo("FULL_TIME, CONTRACTOR");
        assertThat(job.experience()).isEqualTo("경력 3년 이상");
        assertThat(job.team()).isEqualTo("백엔드 개발");
        assertThat(job.sourceUrl()).isEqualTo("https://careers.example.com/careers/backend-2026-01");
        assertThat(job.plainDescription()).isEqualTo("Java와 Spring으로 서비스를 개발합니다.");
        assertThat(job.publishedAt()).isEqualTo(Instant.parse("2026-07-31T15:00:00Z"));
        assertThat(job.updatedAtSource()).isEqualTo(Instant.parse("2026-08-02T01:00:00Z"));
        assertThat(job.deadlineAt()).isEqualTo(Instant.parse("2026-08-31T14:59:59.999999999Z"));
        assertThat(job.contentHash()).hasSize(64);
    }

    private static String fixture() throws IOException {
        try (var input = JsonLdJobPostingParserTest.class.getResourceAsStream(
                "/fixtures/company-page-job-posting.html")) {
            if (input == null) {
                throw new IOException("fixture not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
