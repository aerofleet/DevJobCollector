package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AshbyAdapterTest {
    @Test void collectsListedJobsAndExcludesUnlistedJobs() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo("https://api.ashbyhq.com/posting-api/job-board/friendliai"))
                .andRespond(withSuccess("""
                    {"jobs":[{"isListed":true,"title":"Engineer","location":"Seoul",
                     "jobUrl":"https://jobs.ashbyhq.com/friendliai/123","publishedAt":"2026-01-01T00:00:00Z"},
                     {"isListed":false,"title":"Private","jobUrl":"https://jobs.ashbyhq.com/friendliai/456"}]}
                    """, MediaType.APPLICATION_JSON));
        var result = new AshbyAdapter(client, new ObjectMapper()).fetchJobs(target(), CollectionContext.now());
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.jobs()).singleElement().satisfies(job -> {
            assertThat(job.title()).isEqualTo("Engineer");
            assertThat(job.location()).isEqualTo("Seoul");
            assertThat(job.publishedAt()).isNotNull();
        });
        assertThat(result.closureEvaluationAllowed()).isTrue();
        server.verify();
    }

    @Test void malformedJobDoesNotPermitClosure() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo("https://api.ashbyhq.com/posting-api/job-board/friendliai"))
                .andRespond(withSuccess("{\"jobs\":[{\"isListed\":true,\"title\":\"Incomplete\"}]}", MediaType.APPLICATION_JSON));
        var result = new AshbyAdapter(client, new ObjectMapper()).fetchJobs(target(), CollectionContext.now());
        assertThat(result.status()).isEqualTo(CollectionStatus.PARTIAL_SUCCESS);
        assertThat(result.closureEvaluationAllowed()).isFalse();
        server.verify();
    }

    private static CompanySourceTarget target() {
        return new CompanySourceTarget(null, "FriendliAI", SourceType.ASHBY, "friendliai", "https://jobs.ashbyhq.com/friendliai", CollectionTier.B);
    }
}
