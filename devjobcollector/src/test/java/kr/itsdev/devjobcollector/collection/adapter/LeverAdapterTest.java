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

class LeverAdapterTest {

    @Test
    void retainsSeparateListsAndAdditionalText() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        var adapter = new LeverAdapter(restTemplate, new ObjectMapper());
        var target = new CompanySourceTarget(null, SourceType.LEVER, "example", "https://jobs.lever.co/example", CollectionTier.A);
        server.expect(requestTo("https://api.lever.co/v0/postings/example?mode=json"))
                .andRespond(withSuccess("""
                        [{"id":"1","text":"Engineer","descriptionPlain":"Introduction",
                          "lists":[{"text":"Minimum requirements","content":"<ul><li>Java</li><li>SQL</li></ul>"},
                                   {"text":"Preferred qualifications","content":"<ul><li>Python</li></ul>"}],
                          "salaryDescriptionPlain":"Salary: USD 100,000 per year",
                          "additionalPlain":"Additional information"}]
                        """, MediaType.APPLICATION_JSON));
        assertThat(adapter.fetchJobs(target, CollectionContext.now()).jobs().getFirst().plainDescription())
                .isEqualTo("Introduction\nMinimum requirements\n• Java\n• SQL\nPreferred qualifications\n• Python\nSalary: USD 100,000 per year\nAdditional information");
        server.verify();
    }

    @Test
    void mapsMeasuredLeverFieldsToRawJob() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LeverAdapter adapter = new LeverAdapter(restTemplate, new ObjectMapper());
        CompanySourceTarget target = new CompanySourceTarget(
                null, SourceType.LEVER, "integrate", "https://jobs.lever.co/integrate", CollectionTier.A);

        server.expect(requestTo("https://api.lever.co/v0/postings/integrate?mode=json"))
                .andRespond(withSuccess("""
                        [{
                          "id":"job-456",
                          "text":"Full Stack Engineer",
                          "hostedUrl":"https://jobs.lever.co/integrate/job-456",
                          "applyUrl":"https://jobs.lever.co/integrate/job-456/apply",
                          "descriptionPlain":"Build product features",
                          "createdAt":1785542400000,
                          "workplaceType":"hybrid",
                          "categories":{
                            "commitment":"Full Time",
                            "location":"Seattle, WA",
                            "team":"Engineering",
                            "allLocations":["Seattle, WA"]
                          },
                          "lists":[]
                        }]
                        """, MediaType.APPLICATION_JSON));

        CollectionResult result = adapter.fetchJobs(target, CollectionContext.now());

        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.receivedCount()).isEqualTo(1);
        assertThat(result.jobs().getFirst().sourceJobId()).isEqualTo("job-456");
        assertThat(result.jobs().getFirst().employmentType()).isEqualTo("Full Time");
        assertThat(result.jobs().getFirst().location()).isEqualTo("Seattle, WA");
        assertThat(result.jobs().getFirst().team()).isEqualTo("Engineering");
        assertThat(result.responseHash()).hasSize(64);
        server.verify();
    }
}
