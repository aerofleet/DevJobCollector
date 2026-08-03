package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GreenhouseAdapterTest {

    @Test
    void mapsMeasuredGreenhouseFieldsToRawJob() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GreenhouseAdapter adapter = new GreenhouseAdapter(restTemplate, new ObjectMapper());
        CompanySourceTarget target = new CompanySourceTarget(
                null, SourceType.GREENHOUSE, "gitlab", "https://about.gitlab.com/jobs", CollectionTier.A);

        server.expect(requestTo("https://boards-api.greenhouse.io/v1/boards/gitlab/jobs?content=true"))
                .andRespond(withSuccess("""
                        {"jobs":[{
                          "id":123,
                          "title":"Backend Engineer",
                          "content":"<p>Build reliable APIs</p>",
                          "absolute_url":"https://job-boards.greenhouse.io/gitlab/jobs/123",
                          "first_published":"2026-08-01T01:02:03+00:00",
                          "updated_at":"2026-08-02T02:03:04+00:00",
                          "location":{"name":"Remote"},
                          "departments":[{"id":1,"name":"Engineering"}]
                        }]}
                        """, MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target, CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.receivedCount()).isEqualTo(1);
        assertThat(result.closureEvaluationAllowed()).isTrue();
        assertThat(result.jobs().getFirst().sourceJobId()).isEqualTo("123");
        assertThat(result.jobs().getFirst().team()).isEqualTo("Engineering");
        assertThat(result.jobs().getFirst().plainDescription()).isEqualTo("Build reliable APIs");
        assertThat(result.responseHash()).hasSize(64);
        server.verify();
    }
}
