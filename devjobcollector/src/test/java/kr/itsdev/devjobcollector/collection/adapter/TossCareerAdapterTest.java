package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.CompanyPageCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossCareerAdapterTest {

    @Test
    void mapsPublicJobGroupsAndExcludesHiddenOpening() throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TossCareerAdapter adapter = adapter(restTemplate);
        server.expect(requestTo(TossCareerAdapter.JOB_GROUPS_URL))
                .andExpect(header("User-Agent", "DevJobCollector-Test/1.0"))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target(), CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.receivedCount()).isEqualTo(2);
        assertThat(result.closureEvaluationAllowed()).isTrue();
        assertThat(result.responseHash()).hasSize(64);
        assertThat(result.jobs()).extracting(JobRawDto::title)
                .containsExactly("Technical Product Owner (AI)", "Backend Developer");
        assertThat(result.jobs().getFirst()).satisfies(job -> {
            assertThat(job.sourceJobId()).isEqualTo("6532656003");
            assertThat(job.companyName()).isEqualTo("토스");
            assertThat(job.location()).isEqualTo("Seoul");
            assertThat(job.employmentType()).isEqualTo("정규직");
            assertThat(job.team()).isEqualTo("Product");
            assertThat(job.sourceUrl()).isEqualTo(
                    "https://toss.im/career/job-detail?job_id=6532656003");
            assertThat(job.deadlineAt()).isNull();
        });
        assertThat(result.jobs().get(1).deadlineAt())
                .isEqualTo(Instant.parse("2026-08-31T09:30:00Z"));
        server.verify();
    }

    @Test
    void marksMissingSuccessArrayAsSchemaChanged() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TossCareerAdapter adapter = adapter(restTemplate);
        server.expect(requestTo(TossCareerAdapter.JOB_GROUPS_URL))
                .andRespond(withSuccess("{\"resultType\":\"ERROR\"}", MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target(), CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SCHEMA_CHANGED);
        assertThat(result.jobs()).isEmpty();
        server.verify();
    }

    private static TossCareerAdapter adapter(RestTemplate restTemplate) {
        return new TossCareerAdapter(restTemplate, new ObjectMapper(),
                new CompanyPageCollectionProperties("DevJobCollector-Test/1.0", 20_000));
    }

    private static CompanySourceTarget target() {
        return new CompanySourceTarget(null, "토스 커뮤니티", SourceType.TOSS_CAREERS,
                "toss", "https://toss.im/career", CollectionTier.B);
    }

    private static String fixture() throws IOException {
        try (var input = TossCareerAdapterTest.class.getResourceAsStream(
                "/fixtures/toss-career-job-groups.json")) {
            if (input == null) {
                throw new IOException("fixture not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
