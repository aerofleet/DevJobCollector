package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.CompanyPageCollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CompanyPageAdapterTest {

    @Test
    void collectsPublicJsonLdJobWithoutEnablingClosureEvaluation() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CompanyPageAdapter adapter = adapter(restTemplate);
        String html = """
                <script type="application/ld+json">
                {"@type":"JobPosting","identifier":"job-1","title":"서버 개발자",
                 "hiringOrganization":{"name":"공식회사"},"url":"https://jobs.example.com/job-1"}
                </script>
                """;
        server.expect(requestTo("https://jobs.example.com/openings"))
                .andExpect(header("User-Agent", "DevJobCollector-Test/1.0"))
                .andRespond(withSuccess(html, MediaType.TEXT_HTML));

        CollectionResult result = adapter.fetchJobs(target("https://jobs.example.com/openings"),
                CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.jobs()).hasSize(1);
        assertThat(result.closureEvaluationAllowed()).isFalse();
        assertThat(result.responseHash()).hasSize(64);
        server.verify();
    }

    @Test
    void marksFullyMalformedJsonLdAsSchemaChanged() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CompanyPageAdapter adapter = adapter(restTemplate);
        server.expect(requestTo("https://jobs.example.com/openings"))
                .andRespond(withSuccess("<script type='application/ld+json'>{bad}</script>",
                        MediaType.TEXT_HTML));

        CollectionResult result = adapter.fetchJobs(target("https://jobs.example.com/openings"),
                CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SCHEMA_CHANGED);
        assertThat(result.jobs()).isEmpty();
        server.verify();
    }

    @Test
    void rejectsPrivateNetworkTarget() {
        CompanyPageAdapter adapter = adapter(new RestTemplate());

        CollectionResult result = adapter.fetchJobs(target("http://127.0.0.1:8080/internal"),
                CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.FAILED);
        assertThat(result.jobs()).isEmpty();
    }

    private static CompanyPageAdapter adapter(RestTemplate restTemplate) {
        CompanyPageCollectionProperties properties =
                new CompanyPageCollectionProperties("DevJobCollector-Test/1.0", 20_000);
        JsonLdJobPostingParser parser = new JsonLdJobPostingParser(new ObjectMapper(), properties);
        return new CompanyPageAdapter(restTemplate, parser, properties);
    }

    private static CompanySourceTarget target(String careersUrl) {
        return new CompanySourceTarget(null, "공식회사", SourceType.COMPANY_PAGE,
                "official-company", careersUrl, CollectionTier.B);
    }
}
