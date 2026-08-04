package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.config.SaraminCollectionProperties;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SaraminAdapterTest {

    @Test
    void mapsOfficialApiFieldsAndFixedDeadline() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SaraminAdapter adapter = adapter(restTemplate, 110, 10);

        server.expect(requestTo(containsString("https://oapi.saramin.co.kr/job-search?")))
                .andExpect(requestTo(containsString("access-key=test-key")))
                .andExpect(requestTo(containsString("job_mid_cd=22")))
                .andExpect(header("Accept", containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andRespond(withSuccess(jobResponse(1, 1, "1"), MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target(), new CollectionContext(Instant.EPOCH));

        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.closureEvaluationAllowed()).isTrue();
        assertThat(result.jobs()).singleElement().satisfies(job -> {
            assertThat(job.sourceJobId()).isEqualTo("12345");
            assertThat(job.companyName()).isEqualTo("테스트회사");
            assertThat(job.title()).isEqualTo("백엔드 개발자");
            assertThat(job.location()).isEqualTo("서울 전체");
            assertThat(job.employmentType()).isEqualTo("정규직");
            assertThat(job.experience()).isEqualTo("신입·경력");
            assertThat(job.team()).isEqualTo("IT개발·데이터");
            assertThat(job.deadlineAt()).isEqualTo(Instant.ofEpochSecond(1_786_000_000L));
        });
        assertThat(result.responseHash()).hasSize(64);
        server.verify();
    }

    @Test
    void preventsClosureEvaluationWhenMaxPagesTruncatesResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SaraminAdapter adapter = adapter(restTemplate, 1, 1);
        server.expect(requestTo(containsString("count=1")))
                .andRespond(withSuccess(jobResponse(1, 2, "2"), MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target(), CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.PARTIAL_SUCCESS);
        assertThat(result.closureEvaluationAllowed()).isFalse();
        assertThat(result.jobs().getFirst().deadlineAt()).isNull();
        server.verify();
    }

    @Test
    void mapsApiRateLimitCode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SaraminAdapter adapter = adapter(restTemplate, 110, 10);
        server.expect(requestTo(containsString("job-search")))
                .andRespond(withSuccess("{\"result\":{\"code\":\"4\",\"message\":\"limit\"}}",
                        MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target(), CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.RATE_LIMITED);
        assertThat(result.jobs()).isEmpty();
        server.verify();
    }

    private static SaraminAdapter adapter(RestTemplate restTemplate, int pageSize, int maxPages) {
        return new SaraminAdapter(restTemplate, new ObjectMapper(),
                new SaraminCollectionProperties(true, "test-key", pageSize, maxPages));
    }

    private static CompanySourceTarget target() {
        return new CompanySourceTarget(null, "사람인 IT개발·데이터", SourceType.SARAMIN, "22",
                "https://www.saramin.co.kr/zf_user/jobs/list/job-category", CollectionTier.B);
    }

    private static String jobResponse(int count, int total, String closeType) {
        return """
                {"jobs":{"count":%d,"start":0,"total":%d,"job":[{
                  "id":"12345",
                  "url":"https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=12345",
                  "active":1,
                  "company":{"detail":{"href":"https://example.com","name":"테스트회사"}},
                  "position":{
                    "title":"백엔드 개발자",
                    "location":{"code":"101000","name":"서울 전체"},
                    "job-type":{"code":"1","name":"정규직"},
                    "job-mid-code":{"code":"22","name":"IT개발·데이터"},
                    "experience-level":{"code":"0","name":"신입·경력"}
                  },
                  "posting-timestamp":1754006400,
                  "modification-timestamp":1754092800,
                  "expiration-timestamp":1786000000,
                  "close-type":{"code":"%s","name":"마감일"}
                }]}}
                """.formatted(count, total, closeType);
    }
}
