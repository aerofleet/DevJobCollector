package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.collection.domain.*;
import kr.itsdev.devjobcollector.collection.dto.CollectionContext;
import kr.itsdev.devjobcollector.domain.ExperienceClassification;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoCareerAdapterTest {
    @Test void collectsQualificationAndDutiesWithExplicitEntryAndExperiencedTitle() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo(containsString("part=TECHNOLOGY"))).andRespond(withSuccess("""
            {"jobList":[{"realId":"P-14581","jobOfferTitle":"생성형 모델 Research Engineer (Post-training) (신입/경력)",
              "introduction":"조직 소개","workContentDesc":"- 모델 학습<br/>- 추론 최적화",
              "qualification":"- 석사 이상 혹은 관련 프로젝트 경험<br/>- Python 및 PyTorch 경험<br/><br/>**◆ 우대사항**<br/>- GPU 분산 학습 경험"}],"totalPage":1}
            """, MediaType.APPLICATION_JSON));
        for (String part : new String[]{"DESIGN", "BUSINESS_SERVICES", "STAFF"}) {
            server.expect(requestTo(containsString("part=" + part))).andRespond(withSuccess("{\"jobList\":[],\"totalPage\":1}", MediaType.APPLICATION_JSON));
        }
        var adapter = new KakaoCareerAdapter(client, new ObjectMapper());
        var target = new CompanySourceTarget(null, "카카오", SourceType.KAKAO, "kakao-careers", "https://careers.kakao.com", CollectionTier.A);
        var result = adapter.fetchJobs(target, CollectionContext.now());
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.jobs()).singleElement().satisfies(job -> {
            assertThat(job.experience()).isEqualTo("신입·경력");
            assertThat(job.plainDescription()).contains("업무내용\n- 모델 학습\n- 추론 최적화", "자격요건\n- 석사 이상", "Python 및 PyTorch 경험", "우대사항\n- GPU 분산 학습 경험");
            assertThat(ExperienceClassification.classify(job.experience(), job.title(), job.plainDescription(), false).category()).isEqualTo("BOTH");
        });
        assertThat(KakaoCareerAdapter.experienceFromTitle("(경력 3년 이상)")).isNull();
        server.verify();
    }
}
