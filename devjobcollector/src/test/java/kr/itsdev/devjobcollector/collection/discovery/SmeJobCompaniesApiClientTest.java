package kr.itsdev.devjobcollector.collection.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class SmeJobCompaniesApiClientTest {
    @Test
    void readsLatestSnapshotAndSendsServiceKeyInAuthorizationHeader() throws Exception {
        RestTemplate rest = org.mockito.Mockito.mock(RestTemplate.class);
        var body = new ObjectMapper().readTree("""
                {"totalCount":2,"currentCount":2,"data":[
                  {"기업명":"테크기업","사업자번호":1234567890,"공고명":"백엔드 개발자","공고등록일":"2026-03-06"},
                  {"기업명":"일반기업","사업자번호":2345678901,"공고명":"총무","공고등록일":"2026-03-06"}
                ]}
                """);
        when(rest.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(ResponseEntity.ok(body));
        var client = new SmeJobCompaniesApiClient(rest, new SmeJobCompanySeedReader(),
                "https://api.odcloud.kr/api/15100246/v1/uddi:latest", "approved-key");

        var page = client.fetchPage(1, 1000);

        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.seeds()).hasSize(2);
        assertThat(page.seeds().getFirst().companyName()).isEqualTo("테크기업");
        assertThat(page.seeds().getFirst().businessNumberHash()).matches("[0-9a-f]{64}");
        assertThat(page.seeds().getFirst().jobTitle()).isEqualTo("백엔드 개발자");
        verify(rest).exchange(any(URI.class), eq(HttpMethod.GET), org.mockito.ArgumentMatchers.argThat(
                request -> "Infuser approved-key".equals(request.getHeaders().getFirst("Authorization"))),
                eq(com.fasterxml.jackson.databind.JsonNode.class));
    }
}
