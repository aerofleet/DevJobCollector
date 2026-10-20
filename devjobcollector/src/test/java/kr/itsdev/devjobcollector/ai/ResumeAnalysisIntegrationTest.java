package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.*;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@JdbcTest(properties = {"spring.flyway.enabled=true","spring.flyway.baseline-on-migrate=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named="DJC_MIGRATION_TEST_URL",matches=".+")
@Import({ResumeAnalysisService.class,ResumeExplanationService.class,LocalRecommendationEngine.class,ResumeAnalysisIntegrationTest.JsonConfig.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ResumeAnalysisIntegrationTest {
    @TestConfiguration static class JsonConfig {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ResumeAnalysisService service;
    @Autowired ResumeExplanationService explanations;
    @Autowired ObjectMapper mapper;
    @MockitoBean OpenAiEvidenceClient openai;
    @MockitoBean CurrentMemberService members;
    long user, resume;
    final LocalRecommendationEngine.Preferences prefs = new LocalRecommendationEngine.Preferences(0,"백엔드","","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("DJC_MIGRATION_TEST_URL"));
        r.add("spring.datasource.username",()->"root");
        r.add("spring.datasource.password",()->System.getenv("DJC_MIGRATION_TEST_PASSWORD"));
        r.add("spring.flyway.user",()->"root");
        r.add("spring.flyway.password",()->System.getenv("DJC_MIGRATION_TEST_PASSWORD"));
    }
    @BeforeEach void setup() {
        String email=UUID.randomUUID()+"@example.com";
        jdbc.update("INSERT INTO users(email,name,status) VALUES(?,'analysis-test','ACTIVE')",email);
        user=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        var account=mock(UserAccount.class);
        when(account.getId()).thenReturn(user);
        when(members.requireCurrentMember("owner")).thenReturn(account);
        var other=mock(UserAccount.class);
        when(other.getId()).thenReturn(-1L);
        when(members.requireCurrentMember("other")).thenReturn(other);
        jdbc.update("INSERT INTO resumes(user_id,title,content_json) VALUES(?,'test',?)",user,
                "{\"basicInfo\":{\"email\":\"private@example.com\"},\"techStack\":[],\"projects\":[]}");
        resume=jdbc.queryForObject("SELECT id FROM resumes WHERE user_id=?",Long.class,user);
    }
    @AfterEach void cleanup() { jdbc.update("DELETE FROM users WHERE id=?",user); }
    long create(String key, LocalRecommendationEngine.Preferences p) {
        return service.create("owner",resume,key,ResumeAnalysisService.CONSENT_VERSION,p).path("id").asLong();
    }
    @Test void cachesAndRejectsChangedRequestWithSameKey() {
        String key=UUID.randomUUID().toString();
        long id=create(key,prefs);
        assertThat(create(key,prefs)).isEqualTo(id);
        assertThat(create(UUID.randomUUID().toString(),prefs)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT request_count FROM ai_analysis_daily_usage WHERE user_id=?",Integer.class,user)).isEqualTo(1);
        assertThatThrownBy(()->create(key,new LocalRecommendationEngine.Preferences(2,"백엔드","","")))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(service.get("owner",id).toString()).doesNotContain("private@example.com");
        assertThat(service.get("owner",id).path("externalCallCount").asInt()).isZero();
    }
    @Test void enforcesOwnershipAndCascadeWhilePreservingQuota() {
        long id=create(UUID.randomUUID().toString(),prefs);
        assertThatThrownBy(()->service.get("other",id)).isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode().value()).isEqualTo(404));
        service.delete("owner",id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_audit WHERE analysis_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_idempotency WHERE user_id=?",Integer.class,user)).isZero();
        assertThat(jdbc.queryForObject("SELECT request_count FROM ai_analysis_daily_usage WHERE user_id=?",Integer.class,user)).isEqualTo(1);
        for(int i=1;i<5;i++) create(UUID.randomUUID().toString(),new LocalRecommendationEngine.Preferences(i,"백엔드","",""));
        assertThatThrownBy(()->create(UUID.randomUUID().toString(),new LocalRecommendationEngine.Preferences(5,"백엔드","","")))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(429));
    }
    @Test void concurrentRetriesCreateOneResultAndOneUsage() throws Exception {
        String key=UUID.randomUUID().toString();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(4)) {
            var futures=new java.util.ArrayList<Future<Long>>();
            for(int i=0;i<4;i++) futures.add(pool.submit(()->{start.await(); return create(key,prefs);}));
            start.countDown();
            long id=futures.getFirst().get(20,TimeUnit.SECONDS);
            for(var f:futures) assertThat(f.get(20,TimeUnit.SECONDS)).isEqualTo(id);
        }
        assertThat(jdbc.queryForObject("SELECT request_count FROM ai_analysis_daily_usage WHERE user_id=?",Integer.class,user)).isEqualTo(1);
    }
    @Test void hidesClosedJobsAndPersistsOnlyValidRecommendationFeedback() {
        String sn=UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO job_posts(company_name,title,original_sn,original_url,source_platform,
                start_date,end_date,created_at,job_category,experience)
                VALUES('test','Backend',?,'https://example.com','COMPANY_PAGE',CURRENT_DATE,
                DATE_ADD(CURRENT_DATE,INTERVAL 30 DAY),NOW(),'백엔드','신입')
                """,sn);
        long job=jdbc.queryForObject("SELECT id FROM job_posts WHERE original_sn=?",Long.class,sn);
        try {
            long id=create(UUID.randomUUID().toString(),prefs);
            assertThat(service.get("owner",id).path("recommendations")).hasSize(1);
            service.feedback("owner",id,job,"MATCHED");
            assertThat(service.get("owner",id).path("recommendations").get(0).path("feedback").asText()).isEqualTo("MATCHED");
            assertThatThrownBy(()->service.feedback("owner",id,-1,"MATCHED")).isInstanceOf(ResponseStatusException.class);
            jdbc.update("UPDATE job_posts SET moderation_status='HIDDEN' WHERE id=?",job);
            assertThat(service.get("owner",id).path("recommendations")).isEmpty();
            jdbc.update("DELETE FROM resumes WHERE id=?",resume);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_request WHERE id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_recommendation_feedback WHERE analysis_id=?",Integer.class,id)).isZero();
        } finally { jdbc.update("DELETE FROM job_posts WHERE id=?",job); }
    }

    long seedJob() {
        String sn=UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO job_posts(company_name,title,original_sn,original_url,source_platform,
                start_date,end_date,created_at,job_category,experience)
                VALUES('test','Backend',?,'https://example.com','COMPANY_PAGE',CURRENT_DATE,
                DATE_ADD(CURRENT_DATE,INTERVAL 30 DAY),NOW(),'백엔드','신입')
                """,sn);
        return jdbc.queryForObject("SELECT id FROM job_posts WHERE original_sn=?",Long.class,sn);
    }
    @Test void explanationRequiresConsentCachesAndFiltersHiddenJobs() throws Exception {
        long job=seedJob();
        try {
            long id=create(UUID.randomUUID().toString(),prefs);
            when(openai.available()).thenReturn(true);
            when(openai.select(anyList())).thenReturn(mapper.readTree("{\"jobs\":[{\"slot\":0,\"reasons\":[\"CATEGORY_MATCH\"]}]}"));
            assertThatThrownBy(()->explanations.explain("owner",id,"wrong"))
                    .isInstanceOf(ResponseStatusException.class);
            verify(openai,never()).select(anyList());
            assertThat(explanations.explain("owner",id,ResumeExplanationService.CONSENT_VERSION).path("status").asText()).isEqualTo("COMPLETED");
            explanations.explain("owner",id,ResumeExplanationService.CONSENT_VERSION);
            verify(openai,times(1)).select(anyList());
            assertThatThrownBy(()->explanations.get("other",id)).isInstanceOf(ResponseStatusException.class);
            jdbc.update("UPDATE job_posts SET moderation_status='HIDDEN' WHERE id=?",job);
            assertThat(explanations.get("owner",id).path("jobs")).isEmpty();
            service.delete("owner",id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_evidence_explanation WHERE analysis_id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT attempt_count FROM ai_external_daily_usage WHERE user_id=?",Integer.class,user)).isEqualTo(1);
        } finally { jdbc.update("DELETE FROM job_posts WHERE id=?",job); }
    }
    @Test void failedOrUnsupportedOutputNeverRetriesOrOverwritesLocalAnalysis() throws Exception {
        long job=seedJob();
        try {
            long id=create(UUID.randomUUID().toString(),prefs);
            when(openai.available()).thenReturn(true);
            when(openai.select(anyList())).thenReturn(mapper.readTree("{\"jobs\":[{\"slot\":0,\"reasons\":[\"LOCATION_MATCH\"]}]}"));
            assertThat(explanations.explain("owner",id,ResumeExplanationService.CONSENT_VERSION).path("status").asText()).isEqualTo("FAILED");
            explanations.explain("owner",id,ResumeExplanationService.CONSENT_VERSION);
            verify(openai,times(1)).select(anyList());
            assertThat(service.get("owner",id).path("status").asText()).isEqualTo("COMPLETED_LOCAL");
            assertThat(jdbc.queryForObject("SELECT result_json IS NULL FROM ai_evidence_explanation WHERE analysis_id=?",Boolean.class,id)).isTrue();
        } finally { jdbc.update("DELETE FROM job_posts WHERE id=?",job); }
    }
    @Test void concurrentExplanationRequestUsesDurableClaimWithoutHoldingDbLock() throws Exception {
        long job=seedJob();
        var started=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            long id=create(UUID.randomUUID().toString(),prefs);
            when(openai.available()).thenReturn(true);
            when(openai.select(anyList())).thenAnswer(call->{
                started.countDown();
                if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                return mapper.readTree("{\"jobs\":[{\"slot\":0,\"reasons\":[\"CATEGORY_MATCH\"]}]}");
            });
            var first=pool.submit(()->explanations.explain("owner",id,ResumeExplanationService.CONSENT_VERSION));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->explanations.explain("owner",id,ResumeExplanationService.CONSENT_VERSION));
            try {
                assertThat(second.get(5,TimeUnit.SECONDS).path("status").asText()).isEqualTo("PENDING");
            } finally { release.countDown(); }
            assertThat(first.get(10,TimeUnit.SECONDS).path("status").asText()).isEqualTo("COMPLETED");
            verify(openai,times(1)).select(anyList());
        } finally { release.countDown(); jdbc.update("DELETE FROM job_posts WHERE id=?",job); }
    }
    @Test void dailyExternalLimitPreventsProviderCall() {
        long job=seedJob();
        try {
            long id=create(UUID.randomUUID().toString(),prefs);
            when(openai.available()).thenReturn(true);
            var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            jdbc.update("INSERT INTO ai_external_daily_usage(user_id,usage_date,attempt_count) VALUES(?,?,5)",user,today);
            assertThatThrownBy(()->explanations.explain("owner",id,ResumeExplanationService.CONSENT_VERSION))
                    .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(429));
            verify(openai,never()).select(anyList());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_evidence_explanation WHERE analysis_id=?",Integer.class,id)).isZero();
        } finally { jdbc.update("DELETE FROM job_posts WHERE id=?",job); }
    }
}
