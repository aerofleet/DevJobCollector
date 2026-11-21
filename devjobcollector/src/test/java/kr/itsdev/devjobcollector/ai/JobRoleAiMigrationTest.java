package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.admin.jobs.JobRoleAiReviewService;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
class JobRoleAiMigrationTest {
    @Test void upgradesV64AndStoresFieldProposalsWithoutChangingJobs() throws Exception {
        String schema = "djc_fields_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("DJC_MIGRATION_TEST_URL");
        String user = System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root");
        String password = System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", "");
        var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false);
        var latest = config.load();
        try {
            config.target("64").load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setCatalog(schema);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                jdbc.update("INSERT INTO job_posts (id,source_platform,original_sn,company_name,title,start_date,end_date,original_url,is_active) VALUES (1,'PUBLIC_ALIO','1','Example','전산 연구원','2026-01-01','2030-01-01','https://example.com',1)");
                latest.migrate();
                jdbc.update("INSERT INTO job_field_ai_proposals (job_post_id,job_version,input_hash,policy_version,model,fields_json,review_status,created_at) VALUES (1,0,?,?,'gpt-5.4-mini',?,'PENDING_REVIEW',NOW(6))",
                        "a".repeat(64), JobFieldAiPolicy.VERSION, "{\"fields\":[{\"field\":\"전산직\",\"evidence\":\"전산\"}]}");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_field_ai_proposals WHERE review_status='PENDING_REVIEW'", Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT title FROM job_posts WHERE id=1", String.class)).isEqualTo("전산 연구원");
                assertThat(new JobRoleAiBudget(jdbc).reserveField(1, "b".repeat(64), 2000)).isEqualTo(JobRoleAiBudget.Reservation.RESERVED);
                assertThat(jdbc.queryForObject("SELECT reserved_micro_usd FROM job_role_ai_daily_budget", Long.class)).isEqualTo(3300L);
            }
        } finally { latest.clean(); }
    }
    @Test void upgradesV63WithoutChangingExistingJobs() throws Exception {
        String schema = "djc_roles_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("DJC_MIGRATION_TEST_URL");
        String user = System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root");
        String password = System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", "");
        var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false);
        var latest = config.load();
        try {
            config.target("63").load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setCatalog(schema);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                jdbc.update("""
                        INSERT INTO job_posts (id,source_platform,original_sn,company_name,title,start_date,end_date,original_url,is_active)
                        VALUES (13758,'GREENHOUSE','sample:1','Example','Selection Operations Manager','2026-01-01','2030-01-01','https://example.com',1)
                        """);
                latest.migrate();
                assertThat(jdbc.queryForObject("SELECT title FROM job_posts WHERE id=13758", String.class)).isEqualTo("Selection Operations Manager");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_role_ai_proposals", Integer.class)).isZero();
            }
        } finally {
            latest.clean();
        }
    }

    @Test void migrationAndIdempotentStoreKeepStaleProposalsOut() throws Exception {
        String schema = "djc_roles_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("DJC_MIGRATION_TEST_URL");
        String user = System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root");
        String password = System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", "");
        var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false);
        var flyway = config.load();
        try {
            flyway.migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setCatalog(schema);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                jdbc.update("""
                        INSERT INTO job_posts (id,source_platform,original_sn,company_name,title,start_date,end_date,original_url,is_active)
                        VALUES (13758,'GREENHOUSE','sample:1','Example','Selection Operations Manager','2026-01-01','2030-01-01','https://example.com',1)
                        """);
                var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", "Selection Operations Manager", null,
                        "Run selection operations and lead a team.", "v1", "gpt-5.4-mini");
                var proposal = new JobRoleAiProposalService.Proposal(input.inputHash(), "NEW", "상품 운영 매니저", "Run selection operations");
                var store = new JobRoleAiProposalStore(jdbc, new ObjectMapper());
                assertThat(store.save(13758, 0, "v1", input, proposal)).isEqualTo(JobRoleAiProposalStore.SaveResult.STORED);
                assertThat(store.save(13758, 0, "v1", input, proposal)).isEqualTo(JobRoleAiProposalStore.SaveResult.ALREADY_EXISTS);
                JobPost job = mock(JobPost.class);
                when(job.getId()).thenReturn(13758L);
                when(job.getVersion()).thenReturn(0L);
                when(job.getSourcePlatform()).thenReturn(SourcePlatform.GREENHOUSE);
                when(job.getTitle()).thenReturn("Selection Operations Manager");
                when(job.getApplyQual()).thenReturn("Run selection operations and lead a team.");
                var read = new JobRoleAiReadService(jdbc, new ObjectMapper(), true);
                assertThat(read.approvedRoles(java.util.List.of(job))).isEmpty();
                JobPostRepository jobs = mock(JobPostRepository.class);
                when(jobs.findByIdForModeration(13758L)).thenReturn(Optional.of(job));
                var admin = new AdminPrincipal(1L, "admin@example.com", "admin", AdminRole.ADMIN, Set.of(), "session", "hash");
                long proposalId = jdbc.queryForObject("SELECT id FROM job_role_ai_proposals WHERE job_post_id=13758", Long.class);
                var review = new JobRoleAiReviewService(jobs, jdbc, new ObjectMapper(), mock(JobRoleAiProcessor.class));
                assertThat(review.approve(13758, proposalId, 0, input.inputHash(), admin)).containsEntry("status", "APPROVED");
                assertThat(new JobRoleAiCatalog(jdbc).allowedRoles()).contains("상품 운영 매니저");
                assertThat(read.approvedRoles(java.util.List.of(job))).containsEntry(13758L, "상품 운영 매니저");
                jdbc.update("UPDATE job_posts SET version=1 WHERE id=13758");
                when(job.getVersion()).thenReturn(1L);
                assertThat(read.approvedRoles(java.util.List.of(job))).isEmpty();
                var changedInput = JobRoleAiInput.prepare(13758, "GREENHOUSE", "Selection Operations Manager", null,
                        "New duties.", "v1", "gpt-5.4-mini");
                var changedProposal = new JobRoleAiProposalService.Proposal(changedInput.inputHash(), "NEW", "상품 운영 매니저", "New duties.");
                assertThat(store.save(13758, 0, "v1", changedInput, changedProposal)).isEqualTo(JobRoleAiProposalStore.SaveResult.JOB_CHANGED);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM job_role_ai_proposals", Integer.class)).isEqualTo(1);
                var budget = new JobRoleAiBudget(jdbc);
                for (int i = 1; i <= 17; i++) {
                    assertThat(budget.reserve(13758, String.format("%064x", i), 2000)).isEqualTo(JobRoleAiBudget.Reservation.RESERVED);
                }
                assertThat(budget.reserve(13758, String.format("%064x", 1), 2000)).isEqualTo(JobRoleAiBudget.Reservation.ALREADY_CLAIMED);
                assertThat(budget.reserve(13758, String.format("%064x", 18), 2000)).isEqualTo(JobRoleAiBudget.Reservation.DAILY_LIMIT);
                assertThat(jdbc.queryForObject("SELECT reserved_requests FROM job_role_ai_daily_budget", Integer.class)).isEqualTo(17);
                assertThat(jdbc.queryForObject("SELECT reserved_micro_usd FROM job_role_ai_daily_budget", Long.class)).isEqualTo(48450L);
                String failedHash = String.format("%064x", 1);
                budget.releaseFailedClaim(13758, failedHash);
                assertThat(budget.alreadyClaimed(13758, failedHash)).isFalse();
                assertThat(jdbc.queryForObject("SELECT reserved_requests FROM job_role_ai_daily_budget", Integer.class)).isEqualTo(17);
                assertThat(jdbc.queryForObject("SELECT reserved_micro_usd FROM job_role_ai_daily_budget", Long.class)).isEqualTo(48450L);
                assertThat(budget.reserve(13758, failedHash, 2000)).isEqualTo(JobRoleAiBudget.Reservation.DAILY_LIMIT);
                jdbc.update("DELETE FROM job_role_ai_claims");
                jdbc.update("DELETE FROM job_role_ai_daily_budget");
                int dbStart = url.indexOf('/', "jdbc:mysql://".length());
                int queryStart = url.indexOf('?', dbStart);
                String isolatedUrl = url.substring(0, dbStart + 1) + schema + (queryStart < 0 ? "" : url.substring(queryStart));
                var dataSource = new DriverManagerDataSource(isolatedUrl, user, password);
                var concurrentBudget = new JobRoleAiBudget(new JdbcTemplate(dataSource));
                var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                var executor = Executors.newFixedThreadPool(10);
                var start = new CountDownLatch(1);
                var futures = new ArrayList<Future<JobRoleAiBudget.Reservation>>();
                try {
                    for (int i = 1; i <= 20; i++) {
                        final String hash = String.format("%064x", i);
                        futures.add(executor.submit(() -> {
                            start.await();
                            return tx.execute(status -> concurrentBudget.reserve(13758, hash, 2000));
                        }));
                    }
                    start.countDown();
                    int accepted = 0, denied = 0;
                    for (var future : futures) {
                        var outcome = future.get();
                        if (outcome == JobRoleAiBudget.Reservation.RESERVED) accepted++;
                        if (outcome == JobRoleAiBudget.Reservation.DAILY_LIMIT) denied++;
                    }
                    assertThat(accepted).isEqualTo(17);
                    assertThat(denied).isEqualTo(3);
                    assertThat(jdbc.queryForObject("SELECT reserved_micro_usd FROM job_role_ai_daily_budget", Long.class)).isEqualTo(48450L);
                } finally {
                    executor.shutdownNow();
                }
                jdbc.update("DELETE FROM job_posts WHERE id=13758");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM job_role_ai_proposals", Integer.class)).isZero();
            }
        } finally {
            flyway.clean();
        }
    }
}
