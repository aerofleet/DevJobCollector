package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.Work24CollectionProperties;
import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class Work24AdapterTest {
    private static CompanySourceTarget target(String code) {
        return new CompanySourceTarget(null, "Work24", SourceType.WORK24, code, null, CollectionTier.B);
    }
    private static String page(int total, int page, String id) {
        return "<wantedRoot><total>" + total + "</total><startPage>" + page + "</startPage><wanted>"
                + "<wantedAuthNo>" + id + "</wantedAuthNo><company>Company</company><title>Java Developer</title>"
                + "<busino>1234567890</busino><wantedInfoUrl>https://example.com/job</wantedInfoUrl>"
                + "</wanted></wantedRoot>";
    }
    @Test void fetchesPagesIntoSanitizedReviewSnapshotsWithoutClosure() {
        var http = new RestTemplate();
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo(containsString("occupation=1332"))).andRespond(withSuccess(page(2, 1, "A"), MediaType.APPLICATION_XML));
        server.expect(requestTo(containsString("startPage=2"))).andRespond(withSuccess(page(2, 2, "B"), MediaType.APPLICATION_XML));
        var adapter = new Work24Adapter(http, new ObjectMapper(), new Work24CollectionProperties(true, "key", 1, 3));
        var result = adapter.fetchJobs(target("1332"), CollectionContext.now());
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.closureEvaluationAllowed()).isFalse();
        assertThat(result.jobs()).hasSize(2);
        assertThat(result.jobs().getFirst().rawPayload()).doesNotContain("1234567890");
        server.verify();
    }
    @Test void incompleteAndDuplicatePagesCannotBeComplete() {
        var http = new RestTemplate();
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo(containsString("startPage=1"))).andRespond(withSuccess(page(2, 1, "A"), MediaType.APPLICATION_XML));
        server.expect(requestTo(containsString("startPage=2"))).andRespond(withSuccess(page(2, 2, "A"), MediaType.APPLICATION_XML));
        var result = new Work24Adapter(http, new ObjectMapper(), new Work24CollectionProperties(true, "key", 1, 3))
                .fetchJobs(target("1332"), CollectionContext.now());
        assertThat(result.status()).isEqualTo(CollectionStatus.PARTIAL_SUCCESS);
        assertThat(result.closureEvaluationAllowed()).isFalse();
        server.verify();
    }
    @Test void disabledOrUnfilteredQueriesNeverCallTheApi() {
        var http = new RestTemplate();
        var server = MockRestServiceServer.bindTo(http).build();
        var adapter = new Work24Adapter(http, new ObjectMapper(), new Work24CollectionProperties(true, "key", 100, 2));
        assertThat(adapter.fetchJobs(target("all"), CollectionContext.now()).status()).isEqualTo(CollectionStatus.FAILED);
        assertThat(new Work24Adapter(http, new ObjectMapper(), new Work24CollectionProperties(false, "key", 100, 2))
                .fetchJobs(target("1332"), CollectionContext.now()).status()).isEqualTo(CollectionStatus.FAILED);
        server.verify();
    }
}
