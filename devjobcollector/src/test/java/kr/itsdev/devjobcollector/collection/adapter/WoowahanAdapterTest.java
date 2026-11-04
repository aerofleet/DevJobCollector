package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class WoowahanAdapterTest {
    @Test void usesApiForCollectionButLinksToRecruitmentPage() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo(containsString("/w1/recruits?page=0&size=50")))
                .andRespond(withSuccess("""
                    {"code":"2000","data":{"pageNumber":0,"totalPageNumber":0,"list":[{"recruitNumber":"R2611003","recruitName":"AMD"}]}}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://career.woowahan.com/w1/recruits/R2611003"))
                .andRespond(withSuccess("""
                    {"code":"2000","data":{"recruitNumber":"R2611003","recruitName":"AMD"}}
                    """, MediaType.APPLICATION_JSON));
        var adapter = new WoowahanAdapter(client, new ObjectMapper());
        var target = new CompanySourceTarget(null, "우아한형제들", SourceType.WOOWAHAN, "woowahan", "https://career.woowahan.com", CollectionTier.A);
        var result = adapter.fetchJobs(target, CollectionContext.now());
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.jobs()).singleElement().satisfies(job -> {
            assertThat(job.sourceUrl()).isEqualTo("https://career.woowahan.com/recruitment/R2611003/detail");
            assertThat(job.applyUrl()).isEqualTo(job.sourceUrl());
        });
        server.verify();
    }
}
