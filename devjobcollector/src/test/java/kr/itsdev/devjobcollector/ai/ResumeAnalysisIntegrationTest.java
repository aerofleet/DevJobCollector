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
@Import({ResumeContentReviewService.class,ResumePiiGate.class,ResumeAnalysisService.class,ResumeExplanationService.class,LocalRecommendationEngine.class,SavedResumeAnalysisInput.class,ResumeAnalysisIntegrationTest.JsonConfig.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ResumeAnalysisIntegrationTest {
    @TestConfiguration static class JsonConfig {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ResumeAnalysisService service;
    @Autowired ResumeContentReviewService contentReviews;
    @Autowired ResumeExplanationService explanations;
    @Autowired ObjectMapper mapper;
    @Autowired SavedResumeAnalysisInput savedInput;
    @MockitoBean ResumeTextExtractor extractor;
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
    @Test void contentReviewRequiresExactPreviewAndConsentAndCallsModelOnlyOnce() throws Exception {
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"basicInfo\":{\"name\":\"홍길동\",\"email\":\"private@example.com\"},\"careerProfile\":{\"category\":\"백엔드\",\"location\":\"서울\"},\"projects\":[{\"description\":\"홍길동 Java API를 개발했습니다.\",\"actions\":\"테스트를 작성했습니다.\"}]}", resume);
        when(openai.available()).thenReturn(true);
        var analysis = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, savedInput.prepare("owner", resume));
        long id = analysis.path("id").asLong();
        assertThat(analysis.path("derivedPreferences").path("category")).hasSize(1);
        assertThat(analysis.path("derivedPreferences").path("category").get(0).asText()).isEqualTo("백엔드");
        var preview = contentReviews.preview("owner", id);
        String hash = preview.path("previewHash").asText();
        assertThat(preview.path("payload").toString()).doesNotContain("홍길동", "private@example.com").contains("[익명]", "테스트를 작성했습니다.");
        verify(openai, never()).review(anyMap());
        assertThatThrownBy(() -> contentReviews.review("owner", id, "old", hash)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> contentReviews.review("owner", id, ResumeContentReviewService.CONSENT, "wrong")).isInstanceOf(ResponseStatusException.class);
        verify(openai, never()).review(anyMap());
        String sourceId = "";
        for (var source : preview.path("payload").path("sources")) if (source.path("text").asText().contains("Java API")) sourceId=source.path("sourceId").asText();
        var output = mapper.createObjectNode(); output.putArray("jobs");
        var finding = output.putArray("findings").addObject(); finding.put("sourceId",sourceId); finding.put("quoteId",sourceId + "-P1");
        finding.put("issueType","ROLE"); finding.put("problem","담당 범위를 보완하세요."); finding.putArray("actions").add("구현한 기능을 적으세요."); finding.put("rewriteTemplate","[담당 기능]을 구현했습니다.");
        when(openai.review(anyMap())).thenReturn(output);
        var reviewed = contentReviews.review("owner", id, ResumeContentReviewService.CONSENT, hash);
        assertThat(reviewed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(reviewed.path("findings").size()).isEqualTo(1);
        assertThat(reviewed.path("findings").get(0).path("quote").asText()).isEqualTo("Java API를 개발했습니다.");
        assertThat(reviewed.path("piiGatePassed").asBoolean()).isFalse();
        service.findingFeedback("owner", id, reviewed.path("findings").get(0).path("id").asText(), "INCORRECT");
        assertThat(contentReviews.get("owner", id).path("findings").get(0).path("feedback").asText()).isEqualTo("INCORRECT");
        assertThat(contentReviews.review("owner", id, ResumeContentReviewService.CONSENT, hash).path("findings").size()).isEqualTo(1);
        verify(openai, times(1)).review(anyMap());
        assertThatThrownBy(() -> contentReviews.get("other", id)).isInstanceOf(ResponseStatusException.class);
        service.delete("owner", id);
        assertThatThrownBy(() -> contentReviews.get("owner", id)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM ai_external_daily_usage WHERE user_id=?", Integer.class, user)).isEqualTo(1);
    }
    @Test void concurrentContentReviewKeepsOneAttemptAndDoesNotRetryFailure() throws Exception {
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"projects\":[{\"description\":\"Java API를 개발했습니다.\"}]}", resume);
        when(openai.available()).thenReturn(true);
        var analysis = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, savedInput.prepare("owner", resume));
        long id = analysis.path("id").asLong();
        String hash = contentReviews.preview("owner", id).path("previewHash").asText();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(openai.review(anyMap())).thenAnswer(invocation -> { entered.countDown(); release.await(10, TimeUnit.SECONDS); throw new IllegalStateException("synthetic failure"); });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> contentReviews.review("owner", id, ResumeContentReviewService.CONSENT, hash));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            try { assertThat(contentReviews.review("owner", id, ResumeContentReviewService.CONSENT, hash).path("status").asText()).isEqualTo("PENDING"); }
            finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).path("status").asText()).isEqualTo("FAILED");
        }
        assertThat(contentReviews.review("owner", id, ResumeContentReviewService.CONSENT, hash).path("status").asText()).isEqualTo("FAILED");
        verify(openai, times(1)).review(anyMap());
    }
    @Test void changedResumeInvalidatesContentPreviewBeforeProviderCall() {
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"projects\":[{\"description\":\"Java API를 개발했습니다.\"}]}", resume);
        when(openai.available()).thenReturn(true);
        var analysis = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, savedInput.prepare("owner", resume));
        long id = analysis.path("id").asLong();
        var preview = contentReviews.preview("owner", id);
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"projects\":[{\"description\":\"변경된 내용\"}]}", resume);
        assertThatThrownBy(() -> contentReviews.review("owner", id, ResumeContentReviewService.CONSENT, preview.path("previewHash").asText()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(openai, never()).review(anyMap());
    }
    @Test void verifiedReportPersistsFeedbackChecksOwnershipAndComparesReanalysis() {
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"projects\":[{\"description\":\"API 응답 속도를 개선했습니다.\"}]}", resume);
        jdbc.update("INSERT INTO resume_attachments(resume_id,filename,content_type,byte_size,content) VALUES(?,'resume.pdf','application/pdf',5,?)", resume, "%PDF-".getBytes());
        when(extractor.extractStored(anyString(), anyString(), any())).thenReturn("Java\fAPI 응답 속도를 개선했습니다.");
        var report = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, savedInput.prepare("owner", resume));
        var findings = report.path("review").path("findings");
        assertThat(findings.size()).isEqualTo(1);
        assertThat(findings.get(0).path("locations").size()).isEqualTo(2);
        assertThat(findings.get(0).path("locations").get(1).path("page").asInt()).isEqualTo(2);
        long id = report.path("id").asLong();
        String finding = findings.get(0).path("id").asText();
        service.findingFeedback("owner", id, finding, "INCORRECT");
        assertThat(service.get("owner", id).path("review").path("findings").get(0).path("feedback").asText()).isEqualTo("INCORRECT");
        assertThatThrownBy(() -> service.findingFeedback("other", id, finding, "APPLIED")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findingFeedback("owner", id, "unknown", "APPLIED")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findingFeedback("owner", id, finding, "invalid")).isInstanceOf(ResponseStatusException.class);
        service.findingFeedback("owner", id, finding, "OPEN");
        assertThat(service.get("owner", id).path("review").path("findings").get(0).path("feedback").asText()).isEqualTo("OPEN");
        jdbc.update("DELETE FROM resume_attachments WHERE resume_id=?", resume);
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"projects\":[{\"description\":\"API 응답 시간을 20ms로 단축했습니다.\"}]}", resume);
        var changed = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, savedInput.prepare("owner", resume));
        assertThat(changed.path("comparison").path("notDetected").asInt()).isEqualTo(1);
        assertThat(changed.path("comparison").path("newFindings").asInt()).isZero();
        assertThat(changed.path("review").path("findings")).isEmpty();
    }
    @Test void summaryAndPdfInfrastructureSkillsHaveSourceEvidence() {
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", """
                {"careerProfile":{"summary":"OCI에서 Cloudflare Tunnel을 연결하고 Nginx와 GitHub Actions로 배포했습니다."},"techStack":[]}
                """, resume);
        jdbc.update("INSERT INTO resume_attachments(resume_id,filename,content_type,byte_size,content) VALUES(?,'resume.pdf','application/pdf',5,?)", resume, "%PDF-".getBytes());
        when(extractor.extractStored(anyString(), anyString(), any())).thenReturn("Redis와 RabbitMQ가 메시지를 처리합니다. Prometheus/Grafana로 관측했습니다.");
        var prepared = savedInput.prepare("owner", resume);
        assertThat(prepared.skills()).contains("redis", "rabbitmq", "oci", "cloudflaretunnel", "nginx", "githubactions", "prometheus", "grafana");
        var result = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, prepared);
        var evidence = result.path("review").path("skillEvidence");
        assertThat(evidence.path("redis").get(0).path("page").asInt()).isEqualTo(1);
        assertThat(evidence.path("githubactions").get(0).path("sourceKey").asText()).isEqualTo("/careerProfile/summary");
        assertThat(result.path("review").path("version").asText()).isEqualTo("verified-quotes-v2");
    }

    @Test void savedInputIncludesAttachmentAndInvalidatesCacheWhenRemoved() {
        jdbc.update("INSERT IGNORE INTO tech_stacks(stack_name) VALUES('Java')");
        jdbc.update("INSERT INTO resume_attachments(resume_id,filename,content_type,byte_size,content) VALUES(?,'resume.pdf','application/pdf',5,?)", resume, "%PDF-".getBytes());
        when(extractor.extractStored(anyString(), anyString(), any())).thenReturn("Java Docker project delivered 20% improvement");
        var prepared = savedInput.prepare("owner", resume);
        var result = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, prepared);
        assertThat(result.path("sources").size()).isEqualTo(1);
        assertThat(result.path("sources").get(0).path("filename").asText()).isEqualTo("resume.pdf");
        assertThat(result.path("detectedSkills").toString()).contains("java", "docker");
        assertThat(result.path("derivedPreferences").path("careerYears").isNull()).isTrue();
        assertThat(result.toString()).doesNotContain("private@example.com", "delivered 20%");
        long id = result.path("id").asLong();
        assertThat(service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION,
                savedInput.prepare("owner", resume)).path("id").asLong()).isEqualTo(id);
        jdbc.update("DELETE FROM resume_attachments WHERE resume_id=?", resume);
        assertThatThrownBy(() -> service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, prepared))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION,
                savedInput.prepare("owner", resume)).path("id").asLong()).isNotEqualTo(id);
    }
    @Test void rejectsSavedContentChangedDuringExtractionAndReusesSuccessfulRetry() {
        var prepared = savedInput.prepare("owner", resume);
        jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"techStack\":[\"Java\"]}", resume);
        assertThatThrownBy(() -> service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, prepared))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        String key = UUID.randomUUID().toString();
        var result = service.createSaved("owner", resume, key, ResumeAnalysisService.CONSENT_VERSION, savedInput.prepare("owner", resume));
        assertThat(service.savedRetry("owner", resume, key, ResumeAnalysisService.CONSENT_VERSION).path("id")).isEqualTo(result.path("id"));
        assertThat(service.savedRetry("other", resume, key, ResumeAnalysisService.CONSENT_VERSION)).isNull();
    }
    @Test void attachmentFailureAndOtherOwnerNeverProducePartialAnalysis() {
        assertThatThrownBy(() -> savedInput.prepare("other", resume)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(extractor);
        jdbc.update("INSERT INTO resume_attachments(resume_id,filename,content_type,byte_size,content) VALUES(?,'broken.pdf','application/pdf',5,?)", resume, "%PDF-".getBytes());
        when(extractor.extractStored(anyString(), anyString(), any())).thenThrow(ResumeTextExtractor.failure(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_FAILED"));
        assertThatThrownBy(() -> savedInput.prepare("owner", resume)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_request WHERE user_id=?", Integer.class, user)).isZero();
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
        assertThat(service.get("owner",id).path("rawTextExternalCallCount").asInt()).isZero();
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
            for (String type : java.util.List.of("CAREER_MISMATCH", "SKILL_MISMATCH", "LOCATION_MISMATCH",
                    "ALREADY_SEEN", "NOT_INTERESTED", "INACCURATE_JOB")) {
                service.feedback("owner", id, job, type);
                assertThat(service.get("owner", id).path("recommendations")).isEmpty();
                assertThat(service.get("owner", id).path("excludedRecommendations").get(0).path("feedback").asText()).isEqualTo(type);
                service.feedback("owner", id, job, "MATCHED");
                assertThat(service.get("owner", id).path("recommendations")).hasSize(1);
                assertThat(service.get("owner", id).path("excludedRecommendations")).isEmpty();
            }
            assertThatThrownBy(()->service.feedback("owner",id,-1,"MATCHED")).isInstanceOf(ResponseStatusException.class);
            jdbc.update("UPDATE job_posts SET moderation_status='HIDDEN' WHERE id=?",job);
            assertThat(service.get("owner",id).path("recommendations")).isEmpty();
            jdbc.update("DELETE FROM resumes WHERE id=?",resume);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_request WHERE id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_recommendation_feedback WHERE analysis_id=?",Integer.class,id)).isZero();
        } finally { jdbc.update("DELETE FROM job_posts WHERE id=?",job); }
    }

    @Test void savedMultipleCategoriesReachCandidateQueryAndRanking() throws Exception {
        long backend = seedJob(), frontend = seedJob();
        try {
            jdbc.update("UPDATE job_posts SET job_category='프론트엔드',location='경기',hire_type='인턴' WHERE id=?", frontend);
            jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", """
                    {"careerProfile":{"category":["백엔드","프론트엔드"],"location":["서울","경기"],"employmentType":["정규직","인턴"]},
                    "techStack":[],"projects":[],"experience":[]}
                    """, resume);
            var prepared = savedInput.prepare("owner", resume);
            assertThat(prepared.preferences().category()).containsExactlyInAnyOrder("백엔드", "프론트엔드");
            var result = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, prepared);
            var ids = new java.util.ArrayList<Long>();
            result.path("recommendations").forEach(item -> ids.add(item.path("jobId").asLong()));
            assertThat(ids).contains(backend, frontend);
            var second = java.util.stream.StreamSupport.stream(result.path("recommendations").spliterator(), false)
                    .filter(item -> item.path("jobId").asLong() == frontend).findFirst().orElseThrow();
            assertThat(second.path("components").path("locationFit").asDouble()).isPositive();
            assertThat(second.path("components").path("employmentType").asDouble()).isPositive();
        } finally {
            jdbc.update("DELETE FROM job_posts WHERE id IN (?,?)", backend, frontend);
        }
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
    @Test void untaggedTitleSkillsQualifyWithoutConfusingJavaScriptOrHiddenJobs() {
        long java = seedJob(), javascript = seedJob(), hidden = seedJob(), closed = seedJob();
        try {
            jdbc.update("UPDATE job_posts SET title='Senior Java Engineer' WHERE id=?", java);
            jdbc.update("UPDATE job_posts SET title='JavaScript Engineer' WHERE id=?", javascript);
            jdbc.update("UPDATE job_posts SET title='Java Engineer', moderation_status='HIDDEN' WHERE id=?", hidden);
            jdbc.update("UPDATE job_posts SET title='Java Engineer', end_date=DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY) WHERE id=?", closed);
            jdbc.update("UPDATE resumes SET content_json=? WHERE id=?", "{\"techStack\":[\"Java\"]}", resume);
            var result = service.createSaved("owner", resume, UUID.randomUUID().toString(), ResumeAnalysisService.CONSENT_VERSION, savedInput.prepare("owner", resume));
            assertThat(result.path("recommendations")).hasSize(1);
            var job = result.path("recommendations").get(0);
            assertThat(job.path("jobId").asLong()).isEqualTo(java);
            assertThat(job.path("skillEvidenceSource").asText()).isEqualTo("JOB_TITLE");
            assertThat(job.path("unknownFactors").toString()).contains("requiredSkills");
            assertThat(RecommendationEvidence.project(result).getFirst().evidence())
                    .contains(RecommendationEvidence.Code.TITLE_SKILL_MATCH).doesNotContain(RecommendationEvidence.Code.SKILL_MATCH);
            jdbc.update("UPDATE job_posts SET moderation_status='HIDDEN' WHERE id=?", java);
            assertThat(service.get("owner", result.path("id").asLong()).path("recommendations")).isEmpty();
        } finally { jdbc.update("DELETE FROM job_posts WHERE id IN (?,?,?,?)", java, javascript, hidden, closed); }
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
