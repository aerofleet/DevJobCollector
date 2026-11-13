package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.security.account.AuthProvider;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.PageRequest;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.discovery.AtsFingerprintService;
import kr.itsdev.devjobcollector.collection.discovery.DiscoveryCandidateStore;
import kr.itsdev.devjobcollector.collection.discovery.HomepageAtsLinkDiscovery;
import kr.itsdev.devjobcollector.collection.discovery.ExistingCompanyDiscoveryService;
import kr.itsdev.devjobcollector.collection.discovery.SmeJobCompanySeedImporter;
import kr.itsdev.devjobcollector.collection.discovery.SmeJobCompanySeedReader;
import kr.itsdev.devjobcollector.collection.discovery.SmeJobPostingRelevance;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.mock.web.MockMultipartFile;
import kr.itsdev.devjobcollector.admin.companies.AdminPublicSeedImportService;
import kr.itsdev.devjobcollector.admin.companies.AdminPublicSeedQueryService;
import kr.itsdev.devjobcollector.admin.companies.AdminPublicSeedReviewService;
import kr.itsdev.devjobcollector.admin.companies.AdminPublicSeedDiscoveryService;
import kr.itsdev.devjobcollector.collection.repository.CompanySourceTargetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import kr.itsdev.devjobcollector.admin.companies.AdminDiscoveryService;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import java.util.Set;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import kr.itsdev.devjobcollector.admin.companies.PublicSeedBoardVerificationService;
import kr.itsdev.devjobcollector.admin.companies.PublicSeedTargetApprovalService;
import kr.itsdev.devjobcollector.collection.adapter.JobSourceAdapter;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.dto.CollectionResult;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DJC_MIGRATION_TEST_URL", matches = ".+")
@Import({QuerydslConfig.class, CompanyMembershipService.class, CompanyAuthorizationService.class,
        SmeJobCompanySeedReader.class, SmeJobPostingRelevance.class, SmeJobCompanySeedImporter.class,
        AdminPublicSeedImportService.class})
class CompanyRepositoryIntegrationTest {
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMemberRepository memberRepository;
    @Autowired CompanyMembershipService membershipService;
    @Autowired CompanyAuthorizationService authorizationService;
    @Autowired UserAccountRepository userRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanySourceTargetRepository targetRepository;
    @Autowired AdminAuditLogRepository auditRepository;
    @Autowired AdminPublicSeedImportService seedImportService;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
        registry.add("spring.flyway.user",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME", "root"));
        registry.add("spring.flyway.password",
                () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD", ""));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DJC_KOSME_FIXTURE", matches = ".+")
    void importsOfficialCp949FileIntoIsolatedDatabase() throws Exception {
        byte[] content = Files.readAllBytes(Path.of(System.getenv("DJC_KOSME_FIXTURE")));
        var file = new MockMultipartFile("file", "official.csv", "text/csv", content);
        var admin = new AdminPrincipal(1L, "a@example.com", "관리자", AdminRole.ADMIN, Set.of(), "session", "csrf");
        var counts = seedImportService.importFile(file, "CP949", admin, "official-seed-evaluation", null, null);
        long unique = jdbc.queryForObject("SELECT COUNT(*) FROM public_company_seed", Long.class);
        System.out.println("KOSME_IMPORT_ROWS=" + counts.accepted() + " SKIPPED=" + counts.skipped() + " UNIQUE=" + unique);
        assertThat(counts.accepted()).isPositive();
        assertThat(unique).isPositive().isLessThanOrEqualTo(counts.accepted());
    }

    @Test
    void approvalRequiresBoardEvidenceExplicitOwnershipAndCreatesOneTarget() throws Exception {
        String csv = "기업명,사업자번호,지역구분,종업원수,공고명,고용형태,공고등록일\n"
                + "승인기업,234-56-78901,서울,10,개발자,정규직,2025-01-01\n";
        new SmeJobCompanySeedImporter(new SmeJobCompanySeedReader(), jdbc,
                new kr.itsdev.devjobcollector.collection.discovery.SmeJobPostingRelevance()).importCsv(new StringReader(csv));
        long seedId = jdbc.queryForObject("SELECT id FROM public_company_seed WHERE company_name = ?", Long.class, "승인기업");
        var admin = new AdminPrincipal(1L, "a@example.com", "관리자", AdminRole.ADMIN, Set.of(), "session", "csrf");
        var reviewer = new AdminPrincipal(2L, "r@example.com", "심사자", AdminRole.REVIEWER, Set.of(), "session", "csrf");
        var review = new AdminPublicSeedReviewService(jdbc, auditRepository);
        review.setHomepage(seedId, 0L, "https://example.com", admin, "approval-homepage", null, null);
        var discovery = new AdminPublicSeedDiscoveryService(jdbc, new HomepageAtsLinkDiscovery(new AtsFingerprintService()),
                targetRepository, auditRepository);
        String html = "<a href='https://nuua.career.greetinghr.com/ko/home'>채용</a>";
        discovery.discover(seedId, 1L, html, admin, "approval-discovery", null, null);
        var candidate = discovery.list(seedId, 0, 20).getFirst();
        discovery.startReview(seedId, candidate.id(), 1L, "공식 사이트 확인", "https://example.com/about",
                admin, "approval-review", null, null);
        var approval = new PublicSeedTargetApprovalService(jdbc, targetRepository, new AtsFingerprintService(), auditRepository);
        assertThatThrownBy(() -> approval.approve(seedId, candidate.id(), 1L, "승인기업", true, admin, "unverified", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var adapter = mock(JobSourceAdapter.class);
        when(adapter.sourceType()).thenReturn(SourceType.GREETING);
        var job = new JobRawDto(SourceType.GREETING, "1", "개발자", "서울", null, null,
                "https://nuua.career.greetinghr.com/o/1", null, null, null, null, "hash", "{}");
        when(adapter.fetchJobs(any(), any())).thenReturn(new CollectionResult(CollectionStatus.SUCCESS,
                List.of(job), 1, "test", true, "hash"));
        new PublicSeedBoardVerificationService(jdbc, new AtsFingerprintService(), List.of(adapter), transactionManager, auditRepository)
                .verify(seedId, candidate.id(), 1L, admin, "approval-board", null, null);
        jdbc.update("UPDATE public_seed_ats_candidate SET board_verified_at = UTC_TIMESTAMP(6) - INTERVAL 25 HOUR WHERE id = ?", candidate.id());
        assertThatThrownBy(() -> approval.approve(seedId, candidate.id(), 1L, "승인기업", true, admin, "expired", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE public_seed_ats_candidate SET board_verified_at = UTC_TIMESTAMP(6), review_reference_url = ? WHERE id = ?",
                "https://other.example/about", candidate.id());
        assertThatThrownBy(() -> approval.approve(seedId, candidate.id(), 1L, "승인기업", true, admin, "wrong-evidence", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE public_seed_ats_candidate SET review_reference_url = ? WHERE id = ?", "https://example.com/about", candidate.id());
        assertThatThrownBy(() -> approval.approve(seedId, candidate.id(), 1L, "승인기업", true, reviewer, "reviewer", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> approval.approve(seedId, candidate.id(), 1L, "다른 기업", true, admin, "wrong-name", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> approval.approve(seedId, candidate.id(), 1L, "승인기업", false, admin, "no-attestation", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        long targetId = approval.approve(seedId, candidate.id(), 1L, "승인기업", true, admin, "approval-target", null, null);
        assertThat(targetRepository.findById(targetId).orElseThrow().isCollectable()).isTrue();
        assertThat(discovery.list(seedId, 0, 20).getFirst().status()).isEqualTo("TARGET_CREATED");
        assertThatThrownBy(() -> approval.approve(seedId, candidate.id(), 1L, "승인기업", true, admin, "duplicate", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        auditRepository.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE request_id = ? AND action = ?",
                Integer.class, "approval-target", "PUBLIC_SEED_ATS_TARGET_APPROVED")).isEqualTo(1);
    }

    @Test
    void adminImportsCsvAndRecordsAuditWithoutBusinessNumber() {
        String csv = "기업명,사업자번호,지역구분,종업원수,공고명,고용형태,공고등록일\n"
                + "새기업,123-45-67890,서울,10,개발자,정규직,2025-01-01\n";
        var file = new MockMultipartFile("file", "seed.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var admin = new AdminPrincipal(1L, "a@example.com", "관리자", AdminRole.ADMIN, Set.of(), "session", "csrf");
        assertThat(seedImportService.importFile(file, "UTF-8", admin, "seed-import-test", null, null).accepted()).isEqualTo(1);
        auditRepository.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public_company_seed WHERE company_name = ?",
                Integer.class, "새기업")).isEqualTo(1);
        var query = new AdminPublicSeedQueryService(jdbc);
        var seed = query.list(0, 100).stream().filter(item -> item.companyName().equals("새기업")).findFirst().orElseThrow();
        assertThat(seed.matchedCompanyId()).isNull();
        var review = new AdminPublicSeedReviewService(jdbc, auditRepository);
        review.setHomepage(seed.id(), 0L, "https://example.com/careers", admin, "seed-homepage", null, null);
        auditRepository.flush();
        assertThat(query.list(0, 100)).anyMatch(item -> item.id() == seed.id() && item.version() == 1
                && "https://example.com/careers".equals(item.homepageCandidateUrl()));
        assertThatThrownBy(() -> review.setHomepage(seed.id(), 0L, "https://other.example", admin,
                "stale", null, null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> review.setHomepage(seed.id(), 1L, "http://example.com", admin,
                "invalid", null, null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE request_id = ?", Integer.class,
                "seed-homepage")).isEqualTo(1);
        var discovery = new AdminPublicSeedDiscoveryService(jdbc,
                new HomepageAtsLinkDiscovery(new AtsFingerprintService()), targetRepository, auditRepository);
        String html = "<a href='https://nuua.career.greetinghr.com/ko/home'>채용</a>";
        assertThat(discovery.discover(seed.id(), 1L, html, admin, "seed-discovery", null, null)).isEqualTo(1);
        assertThat(discovery.discover(seed.id(), 1L, html, admin, "seed-discovery-2", null, null)).isEqualTo(1);
        assertThat(discovery.list(seed.id(), 0, 20)).hasSize(1)
                .first().extracting(AdminPublicSeedDiscoveryService.CandidateView::sourceIdentifier).isEqualTo("nuua");
        var first = discovery.list(seed.id(), 0, 20).getFirst();
        assertThat(first.firstHomepageCandidateUrl()).isEqualTo("https://example.com/careers");
        assertThat(first.firstSeedVersion()).isEqualTo(1L);
        assertThat(first.currentSeedVersion()).isEqualTo(1L);
        assertThatThrownBy(() -> discovery.startReview(seed.id(), first.id(), 0L, "확인 중", null,
                admin, "review-stale", null, null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        discovery.startReview(seed.id(), first.id(), 1L, "기업 소개 페이지 확인 필요", "https://example.com/about",
                admin, "ats-review", null, null);
        auditRepository.flush();
        var reviewing = discovery.list(seed.id(), 0, 20).getFirst();
        assertThat(reviewing.status()).isEqualTo("VERIFYING");
        assertThat(reviewing.reviewNote()).isEqualTo("기업 소개 페이지 확인 필요");
        assertThat(reviewing.reviewReferenceUrl()).isEqualTo("https://example.com/about");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE request_id = ? AND action = ?",
                Integer.class, "ats-review", "PUBLIC_SEED_ATS_REVIEW_STARTED")).isEqualTo(1);
        var adapter = mock(JobSourceAdapter.class);
        when(adapter.sourceType()).thenReturn(SourceType.GREETING);
        var verifier = new PublicSeedBoardVerificationService(jdbc, new AtsFingerprintService(), List.of(adapter),
                transactionManager, auditRepository);
        when(adapter.fetchJobs(any(), any())).thenReturn(CollectionResult.failed(CollectionStatus.FAILED, "test"));
        assertThatThrownBy(() -> verifier.verify(seed.id(), first.id(), 1L, admin, "board-failed", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(discovery.list(seed.id(), 0, 20).getFirst().status()).isEqualTo("VERIFYING");
        var job = new JobRawDto(SourceType.GREETING, "1", "개발자", "서울", null, null,
                "https://nuua.career.greetinghr.com/o/1", null, null, null, null, "hash", "{}");
        when(adapter.fetchJobs(any(), any())).thenReturn(new CollectionResult(CollectionStatus.SUCCESS,
                List.of(job), 1, "test", true, "hash"));
        assertThat(verifier.verify(seed.id(), first.id(), 1L, admin, "board-ok", null, null)).isEqualTo(1);
        assertThat(discovery.list(seed.id(), 0, 20).getFirst().status()).isEqualTo("BOARD_VERIFIED");
        discovery.reject(seed.id(), first.id(), "기업 소유권 미확인", admin, "ats-reject", null, null);
        auditRepository.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE request_id = ? AND action = ?",
                Integer.class, "ats-reject", "PUBLIC_SEED_ATS_REJECTED")).isEqualTo(1);
        assertThatThrownBy(() -> discovery.reject(seed.id(), first.id(), "중복 거절", admin, "repeat", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> discovery.reject(seed.id() + 10000, first.id(), "다른 기업", admin, "wrong", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        review.setHomepage(seed.id(), 1L, "https://other.example", admin, "seed-homepage-new", null, null);
        assertThat(discovery.discover(seed.id(), 2L, html, admin, "rediscovered", null, null)).isEqualTo(1);
        var afterChange = discovery.list(seed.id(), 0, 20).getFirst();
        assertThat(afterChange.firstHomepageCandidateUrl()).isEqualTo("https://example.com/careers");
        assertThat(afterChange.firstSeedVersion()).isEqualTo(1L);
        assertThat(afterChange.currentSeedVersion()).isEqualTo(2L);
        assertThat(afterChange.status()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> discovery.discover(seed.id(), 0L, html, admin, "stale-discovery", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(targetRepository.findByProviderAndSourceIdentifier(SourceType.GREETING, "nuua")).isEmpty();
        String hash = jdbc.queryForObject("SELECT business_number_hash FROM public_company_seed WHERE id = ?",
                String.class, seed.id());
        var matched = saveCompany(saveUser("seed-match-owner@example.com"), hash);
        matched.changeStatus(CompanyStatus.VERIFIED);
        companyRepository.flush();
        assertThat(query.list(0, 100)).anyMatch(item -> item.id() == seed.id()
                && matched.getId().equals(item.matchedCompanyId()) && "VERIFIED".equals(item.companyStatus()));
        assertThat(query.list(seed.id(), 100)).noneMatch(item -> item.id() == seed.id());
        assertThatThrownBy(() -> query.list(-1, 20)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT after_json FROM admin_audit_logs WHERE request_id = ?",
                String.class, "seed-import-test")).doesNotContain("1234567890", "123-45-67890");
    }

    @Test
    void stagesPublicCompanySeedsWithoutCreatingCompaniesOrTargets() throws Exception {
        String csv = "기업명,사업자번호,지역구분,종업원수,공고명,고용형태,공고등록일\r\n"
                + "공공기업,123-45-67890,서울,10,개발자,정규직,2025-01-01\r\n"
                + "공공기업,1234567890,서울,10,개발자,정규직,2025-01-02\r\n";
        var importer = new SmeJobCompanySeedImporter(new SmeJobCompanySeedReader(), jdbc, new kr.itsdev.devjobcollector.collection.discovery.SmeJobPostingRelevance());
        var counts = importer.importCsv(new StringReader(csv));
        assertThat(counts).isEqualTo(new SmeJobCompanySeedReader.Counts(2, 0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public_company_seed WHERE company_name = ?",
                Integer.class, "공공기업")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT business_number_hash FROM public_company_seed WHERE company_name = ?",
                String.class, "공공기업")).matches("[0-9a-f]{64}").doesNotContain("1234567890");
        assertThat(jdbc.queryForObject("SELECT has_it_related_posting FROM public_company_seed WHERE company_name = ?",
                Boolean.class, "공공기업")).isTrue();
        assertThat(jdbc.queryForObject("SELECT relevant_posting_title FROM public_company_seed WHERE company_name = ?",
                String.class, "공공기업")).isEqualTo("개발자");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM companies WHERE display_name = ?",
                Integer.class, "공공기업")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_source_target WHERE company_name = ?",
                Integer.class, "공공기업")).isZero();
        jdbc.update("UPDATE public_company_seed SET status = 'REJECTED' WHERE company_name = ?", "공공기업");
        importer.importCsv(new StringReader(csv));
        assertThat(jdbc.queryForObject("SELECT status FROM public_company_seed WHERE company_name = ?",
                String.class, "공공기업")).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT has_it_related_posting FROM public_company_seed WHERE company_name = ?",
                Boolean.class, "공공기업")).isTrue();
    }

    @Test
    void processesEligibleCompanyWithoutCreatingTargetsOrDuplicatingKnownBoards() {
        UserAccount owner = saveUser("discovery-flow-owner@example.com");
        Company company = saveCompany(owner, "6".repeat(64));
        company.changeStatus(CompanyStatus.VERIFIED);
        companyRepository.flush();
        targetRepository.saveAndFlush(new CompanySourceTarget(null, "기존 기업", SourceType.LEVER,
                "existing-board", "https://jobs.lever.co/existing-board", CollectionTier.B));
        var service = new ExistingCompanyDiscoveryService(companyRepository, targetRepository,
                new HomepageAtsLinkDiscovery(new AtsFingerprintService()), new DiscoveryCandidateStore(jdbc));
        String html = "<a href='https://nuua.career.greetinghr.com/ko/home'>채용</a>"
                + "<a href='https://jobs.lever.co/existing-board'>기존</a>";

        assertThat(service.process(company.getId(), "https://example.com", html)).isEqualTo(1);
        assertThat(service.process(company.getId(), "https://example.com", html)).isEqualTo(1);
        var queue = new AdminDiscoveryService(jdbc, auditRepository);
        assertThat(queue.list(null, 0, 100)).anyMatch(candidate -> candidate.companyId() == company.getId());
        var queued = queue.list("DISCOVERED", 0, 100);
        assertThat(queued).anyMatch(candidate -> candidate.companyId() == company.getId()
                && candidate.sourceIdentifier().equals("nuua"));
        long candidateId = queued.stream().filter(candidate -> candidate.companyId() == company.getId())
                .findFirst().orElseThrow().id();
        assertThat(queue.list("DISCOVERED", candidateId, 100))
                .noneMatch(candidate -> candidate.id() == candidateId);
        assertThat(queue.list("REJECTED", 0, 100))
                .noneMatch(candidate -> candidate.companyId() == company.getId());
        assertThatThrownBy(() -> queue.list("unknown", 0, 20)).isInstanceOf(
                org.springframework.web.server.ResponseStatusException.class);
        queue.reject(candidateId, "기업 관계 미확인", new AdminPrincipal(1L, "a@example.com", "관리자",
                AdminRole.REVIEWER, Set.of(), "session", "csrf"), "discovery-test", null, null);
        auditRepository.flush();
        assertThat(queue.list("REJECTED", 0, 100)).anyMatch(candidate -> candidate.id() == candidateId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE request_id = ? AND action = ?",
                Integer.class, "discovery-test", "DISCOVERY_CANDIDATE_REJECTED")).isEqualTo(1);
        assertThatThrownBy(() -> queue.reject(candidateId, "중복", new AdminPrincipal(1L, "a@example.com", "관리자",
                AdminRole.REVIEWER, Set.of(), "session", "csrf"), "second", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_discovery_candidate WHERE company_id = ?",
                Integer.class, company.getId())).isEqualTo(1);
        assertThat(targetRepository.existsByCompanyId(company.getId())).isFalse();
        assertThatThrownBy(() -> service.process(company.getId(), "https://other.example", html))
                .isInstanceOf(IllegalArgumentException.class);
        company.changeStatus(CompanyStatus.SUSPENDED);
        assertThatThrownBy(() -> service.process(company.getId(), "https://example.com", html))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesDiscoveryOnlyOnceAndPreservesReviewStatus() {
        UserAccount owner = saveUser("discovery-candidate-owner@example.com");
        Company company = saveCompany(owner, "5".repeat(64));
        companyRepository.flush();
        var discovery = new HomepageAtsLinkDiscovery(new AtsFingerprintService());
        var store = new DiscoveryCandidateStore(jdbc);
        var candidates = discovery.discover("https://example.com/",
                "<a href='https://nuua.career.greetinghr.com/ko/home'>채용</a>");
        store.record(company.getId(), candidates);
        jdbc.update("UPDATE company_discovery_candidate SET status = 'REJECTED' WHERE company_id = ?", company.getId());
        store.record(company.getId(), candidates);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_discovery_candidate WHERE company_id = ?",
                Integer.class, company.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM company_discovery_candidate WHERE company_id = ?",
                String.class, company.getId())).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_source_target WHERE company_id = ?",
                Integer.class, company.getId())).isZero();
    }

    @Test
    void discoversOnlyVerifiedCompaniesWithHomepageAndNoLinkedTarget() {
        UserAccount owner = saveUser("discovery-owner@example.com");
        Company eligible = saveCompany(owner, "1".repeat(64));
        eligible.changeStatus(CompanyStatus.VERIFIED);
        Company linked = saveCompany(owner, "2".repeat(64));
        linked.changeStatus(CompanyStatus.VERIFIED);
        Company pending = saveCompany(owner, "3".repeat(64));
        Company noHomepage = companyRepository.save(Company.pendingVerification(
                "홈페이지 없음", "홈페이지 없음", "4".repeat(64), "***-**-12345", null, owner));
        noHomepage.changeStatus(CompanyStatus.VERIFIED);
        companyRepository.flush();
        entityManager.persist(new CompanySourceTarget(linked.getId(), "연결 기업", SourceType.GREETING,
                "linked-company", "https://linked-company.career.greetinghr.com/ko/home", CollectionTier.B));
        entityManager.flush();
        entityManager.clear();

        var ids = companyRepository.findDiscoverySeeds(CompanyStatus.VERIFIED, PageRequest.of(0, 100))
                .map(Company::getId).getContent();
        assertThat(ids).contains(eligible.getId()).doesNotContain(linked.getId(), pending.getId(), noHomepage.getId());
    }

    @Test
    void persistsCompanyAndQueriesMembershipByCompanyAndUser() {
        UserAccount owner = saveUser("repository-owner@example.com");
        Company company = saveCompany(owner, "a".repeat(64));
        CompanyMember membership = memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, owner, LocalDateTime.of(2026, 9, 7, 10, 0)));
        entityManager.clear();

        CompanyMember found = memberRepository
                .findByCompany_IdAndUser_Id(company.getId(), owner.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(membership.getId());
        assertThat(found.isActiveOwner()).isTrue();
        assertThat(companyRepository.existsByBusinessNumberHash("a".repeat(64))).isTrue();
        assertThat(memberRepository.findAllByUser_IdAndStatusOrderByCompany_IdAsc(
                owner.getId(), CompanyMemberStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void rejectsDuplicateCompanyMembership() {
        UserAccount owner = saveUser("duplicate-member@example.com");
        Company company = saveCompany(owner, "b".repeat(64));
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 7, 10, 0);
        memberRepository.saveAndFlush(CompanyMember.activeOwner(company, owner, joinedAt));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                CompanyMember.activeOwner(company, owner, joinedAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesLastActiveOwnerInvariantAndAllowsTransferAfterSecondOwnerIsActive() {
        UserAccount firstOwner = saveUser("first-owner@example.com");
        UserAccount secondOwner = saveUser("second-owner@example.com");
        Company company = saveCompany(firstOwner, "d".repeat(64));
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 7, 10, 0);
        memberRepository.save(CompanyMember.activeOwner(company, firstOwner, joinedAt));
        memberRepository.flush();

        assertThatThrownBy(() -> membershipService.changeRole(
                company.getId(), firstOwner.getId(), CompanyMemberRole.ADMIN))
                .isInstanceOf(LastActiveOwnerException.class);

        memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, secondOwner, joinedAt.plusMinutes(1)));
        membershipService.changeRole(company.getId(), firstOwner.getId(), CompanyMemberRole.ADMIN);
        entityManager.flush();
        entityManager.clear();

        assertThat(memberRepository.findByCompany_IdAndUser_Id(company.getId(), firstOwner.getId()))
                .get()
                .extracting(CompanyMember::getRole)
                .isEqualTo(CompanyMemberRole.ADMIN);
        assertThat(memberRepository.countByCompany_IdAndRoleAndStatus(
                company.getId(), CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    void authorizesActiveMembershipForVerifiedCompany() {
        UserAccount owner = saveUser("authorized-owner@example.com");
        Company company = saveCompany(owner, "e".repeat(64));
        company.changeStatus(CompanyStatus.VERIFIED);
        companyRepository.save(company);
        CompanyMember membership = memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, owner, LocalDateTime.of(2026, 9, 8, 10, 0)));
        entityManager.clear();

        CompanyMember authorized = authorizationService.authorize(
                company.getId(), owner.getId(), CompanyPermission.EDIT_COMPANY);

        assertThat(authorized.getId()).isEqualTo(membership.getId());
    }

    @Test
    void rejectsActiveMembershipWhenCompanyIsNotVerified() {
        UserAccount owner = saveUser("pending-company-owner@example.com");
        Company company = saveCompany(owner, "f".repeat(64));
        memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, owner, LocalDateTime.of(2026, 9, 8, 10, 0)));
        entityManager.clear();

        assertThatThrownBy(() -> authorizationService.authorize(
                company.getId(), owner.getId(), CompanyPermission.EDIT_COMPANY))
                .isInstanceOf(CompanyAuthorizationException.class)
                .hasMessage("COMPANY_NOT_VERIFIED");
    }

    private UserAccount saveUser(String email) {
        return userRepository.save(UserAccount.activeSocial(
                email, email, AuthProvider.GITHUB, "subject-" + email));
    }

    private Company saveCompany(UserAccount creator, String hash) {
        return companyRepository.save(Company.pendingVerification(
                "테스트 주식회사", "테스트", hash, "***-**-12345",
                "https://example.com", creator));
    }
}
