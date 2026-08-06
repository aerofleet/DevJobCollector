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

import java.time.Instant;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NaverCareerAdapterTest {

    @Test
    void collectsAllAffiliatesAcrossPagesAndMapsDetailFields() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        NaverCareerAdapter adapter = adapter(restTemplate);

        server.expect(requestTo(NaverCareerAdapter.LIST_URL + "?firstIndex=0"))
                .andExpect(header("User-Agent", "DevJobCollector-Test/1.0"))
                .andRespond(withSuccess(page(2, """
                        {
                          "annoId":30001,
                          "sysCompanyCdNm":"NAVER",
                          "annoSubject":"AI 엔지니어",
                          "entTypeCdNm":"경력",
                          "workAreaCd":"0010",
                          "staYmdTime":"2026.08.01 09:00:00",
                          "endYmdTime":"2026.08.31 18:00:00",
                          "classCdNm":"Tech",
                          "subJobCdNm":"AI/ML",
                          "empTypeCdNm":"정규",
                          "jobDetailLink":"https://recruit.navercorp.com/rcrt/view.do?annoId=30001"
                        }
                        """), MediaType.APPLICATION_JSON));
        server.expect(requestTo(NaverCareerAdapter.LIST_URL + "?firstIndex=1"))
                .andRespond(withSuccess(page(2, """
                        {
                          "annoId":30002,
                          "sysCompanyCdNm":"NAVER Cloud",
                          "annoSubject":"Cloud Engineer",
                          "entTypeCdNm":"무관",
                          "workAreaCd":"0050",
                          "staYmdTime":"2026.08.02 10:00:00",
                          "endYmdTime":"2999.12.31 23:59:59",
                          "classCdNm":"Tech",
                          "subJobCdNm":"Infra Engineering",
                          "empTypeCdNm":"정규",
                          "jobDetailLink":"https://recruit.navercorp.com/rcrt/view.do?annoId=30002"
                        }
                        """), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://recruit.navercorp.com/rcrt/view.do?annoId=30001"))
                .andRespond(withSuccess(detail("경기도 성남시 분당구 정자일로 95",
                        "Who We Are", "AI 서비스를 개발합니다."), utf8Html()));
        server.expect(requestTo("https://recruit.navercorp.com/rcrt/view.do?annoId=30002"))
                .andRespond(withSuccess(detail("", "Required Skills", "Java 경험"),
                        utf8Html()));

        CollectionResult result = adapter.fetchJobs(target(), CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.receivedCount()).isEqualTo(2);
        assertThat(result.closureEvaluationAllowed()).isTrue();
        assertThat(result.responseHash()).hasSize(64);
        assertThat(result.jobs()).extracting(JobRawDto::companyName)
                .containsExactly("NAVER", "NAVER Cloud");
        assertThat(result.jobs().getFirst()).satisfies(job -> {
            assertThat(job.provider()).isEqualTo(SourceType.NAVER_CAREERS);
            assertThat(job.sourceJobId()).isEqualTo("30001");
            assertThat(job.location()).isEqualTo("경기도 성남시 분당구 정자일로 95");
            assertThat(job.employmentType()).isEqualTo("정규");
            assertThat(job.experience()).isEqualTo("경력");
            assertThat(job.team()).isEqualTo("Tech / AI/ML");
            assertThat(job.plainDescription()).isEqualTo("Who We Are AI 서비스를 개발합니다.");
            assertThat(job.publishedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
            assertThat(job.deadlineAt()).isEqualTo(Instant.parse("2026-08-31T09:00:00Z"));
        });
        assertThat(result.jobs().get(1).location()).isEqualTo("Global");
        assertThat(result.jobs().get(1).deadlineAt()).isNull();
        server.verify();
    }

    @Test
    void keepsListJobWhenDetailRequestFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        NaverCareerAdapter adapter = adapter(restTemplate);
        server.expect(requestTo(NaverCareerAdapter.LIST_URL + "?firstIndex=0"))
                .andRespond(withSuccess(page(1, """
                        {
                          "annoId":30003,
                          "sysCompanyCdNm":"SNOW",
                          "annoSubject":"Security Engineer",
                          "entTypeCdNm":"경력",
                          "workAreaCd":"0020",
                          "staYmdTime":"2026.08.01 09:00:00",
                          "endYmdTime":"2026.08.31 18:00:00",
                          "classCdNm":"Tech",
                          "subJobCdNm":"Security",
                          "empTypeCdNm":"정규",
                          "jobDetailLink":"https://recruit.navercorp.com/rcrt/view.do?annoId=30003"
                        }
                        """), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://recruit.navercorp.com/rcrt/view.do?annoId=30003"))
                .andRespond(withServerError());

        CollectionResult result = adapter.fetchJobs(target(), CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.PARTIAL_SUCCESS);
        assertThat(result.receivedCount()).isEqualTo(1);
        assertThat(result.closureEvaluationAllowed()).isTrue();
        assertThat(result.jobs().getFirst().companyName()).isEqualTo("SNOW");
        assertThat(result.jobs().getFirst().location()).isEqualTo("서울");
        assertThat(result.jobs().getFirst().plainDescription()).isNull();
        server.verify();
    }

    @Test
    void marksMissingTotalSizeAsSchemaChanged() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        NaverCareerAdapter adapter = adapter(restTemplate);
        server.expect(requestTo(NaverCareerAdapter.LIST_URL + "?firstIndex=0"))
                .andRespond(withSuccess("{\"list\":[]}", MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target(), CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SCHEMA_CHANGED);
        assertThat(result.jobs()).isEmpty();
        assertThat(result.closureEvaluationAllowed()).isFalse();
        server.verify();
    }

    private static NaverCareerAdapter adapter(RestTemplate restTemplate) {
        return new NaverCareerAdapter(restTemplate, new ObjectMapper(),
                new CompanyPageCollectionProperties("DevJobCollector-Test/1.0", 20_000));
    }

    private static CompanySourceTarget target() {
        return new CompanySourceTarget(null, "네이버 계열사", SourceType.NAVER_CAREERS,
                "naver-careers", "https://recruit.navercorp.com/rcrt/list.do", CollectionTier.B);
    }

    private static String page(int totalSize, String job) {
        return "{\"totalSize\":" + totalSize + ",\"list\":[" + job + "]}";
    }

    private static String detail(String streetAddress, String title, String description) {
        return """
                <html><head>
                  <script type="application/ld+json">
                    {"@type":"JobPosting","jobLocation":{"address":{"streetAddress":"%s"}}}
                  </script>
                </head><body>
                  <div class="detail_box">
                    <h4 class="detail_title">%s</h4>
                    <p class="detail_text">%s</p>
                  </div>
                </body></html>
                """.formatted(streetAddress, title, description);
    }

    private static MediaType utf8Html() {
        return new MediaType("text", "html", StandardCharsets.UTF_8);
    }
}
