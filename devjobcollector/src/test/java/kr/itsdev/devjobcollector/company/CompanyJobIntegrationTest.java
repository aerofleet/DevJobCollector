package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import java.time.*;
import java.util.*;
import kr.itsdev.devjobcollector.company.jobs.*;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import kr.itsdev.devjobcollector.domain.*;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.security.account.*;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named="DJC_MIGRATION_TEST_URL",matches=".+")
@Import({QuerydslConfig.class, CompanyJobService.class, CompanyAuthorizationService.class, CompanyJobEngagementController.class})
class CompanyJobIntegrationTest {
    @Autowired CompanyJobService service;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMemberRepository members;
    @Autowired UserAccountRepository users;
    @Autowired JobPostRepository posts;
    @Autowired CompanyJobRepository drafts;
    @Autowired CompanyJobAuditRepository audits;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyJobEngagementController engagement;
    @MockitoBean CurrentMemberService current;
    Company company;
    UserAccount user;
    static final AdminPrincipal ADMIN=new AdminPrincipal(1L,"admin@example.com","admin",AdminRole.ADMIN,Set.of(),"session","csrf");

    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("DJC_MIGRATION_TEST_URL"));
        r.add("spring.datasource.username", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME","root"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD",""));
        r.add("spring.flyway.user", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME","root"));
        r.add("spring.flyway.password", () -> System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD",""));
    }
    @BeforeEach void setup() {
        String key=UUID.randomUUID().toString();
        user=users.save(UserAccount.activeSocial(key+"@example.com","owner",AuthProvider.GITHUB,key));
        company=companies.save(Company.pendingVerification("기업", "검증기업", key.replace("-","").repeat(2),"123-**-45678",null,user));
        company.changeStatus(CompanyStatus.VERIFIED);
        CompanyMember member=CompanyMember.invited(company,user,CompanyMemberRole.OWNER,user);
        member.changeStatus(CompanyMemberStatus.ACTIVE,LocalDateTime.now()); members.saveAndFlush(member);
        when(current.requireCurrentMember("owner")).thenReturn(user);
    }
    CompanyJobContent.Input input(String title) { return new CompanyJobContent.Input(title,"backend","신입","서울","정규직","Java,Spring","업무 내용",LocalDate.now().plusDays(10),"https://example.com/jobs"); }
    CompanyJobService.View create() { return service.create("owner",company.getId(),input("백엔드 개발자")); }
    CompanyJobService.View publish() {
        var d=create(); var submitted=service.submit("owner",company.getId(),d.id(),d.version());
        return service.review(d.id(),submitted.version(),"APPROVE","확인 완료",ADMIN);
    }
    @Test void draftIsNotPublicAndApprovalProjectsSearchableJob() {
        long before=posts.countActiveAndValid(LocalDate.now());
        var d=create(); assertThat(d.publishedJobId()).isNull();
        assertThat(posts.countActiveAndValid(LocalDate.now())).isEqualTo(before);
        var s=service.submit("owner",company.getId(),d.id(),d.version());
        assertThat(s.publishedJobId()).isNull();
        var p=service.review(d.id(),s.version(),"APPROVE","확인",ADMIN);
        assertThat(posts.countActiveAndValid(LocalDate.now())).isEqualTo(before+1);
        assertThat(posts.findByTechStackNamesOptimized(List.of("Java", "Spring"),LocalDate.now(),PageRequest.of(0,100)).getContent()
                .stream().filter(j -> j.getId().equals(p.publishedJobId()))).hasSize(1);
        assertThat(audits.findTop50ByCompanyJobIdOrderByIdDesc(d.id())).hasSize(3);
    }
    @Test void editAndRejectionPreserveApprovedContentUntilApproval() {
        var p=publish(); var e=service.edit("owner",company.getId(),p.id(),p.version(),input("수정된 공고"));
        assertThat(posts.findById(p.publishedJobId()).orElseThrow().getTitle()).isEqualTo("백엔드 개발자");
        var s=service.submit("owner",company.getId(),e.id(),e.version());
        var rejected=service.review(s.id(),s.version(),"REQUEST_CHANGES","내용 보완",ADMIN);
        assertThat(rejected.liveStatus()).isEqualTo("ACTIVE");
        var again=service.submit("owner",company.getId(),rejected.id(),rejected.version());
        var approved=service.review(again.id(),again.version(),"APPROVE","보완 확인",ADMIN);
        assertThat(approved.publishedJobId()).isEqualTo(p.publishedJobId());
        assertThat(posts.findById(p.publishedJobId()).orElseThrow().getTitle()).isEqualTo("수정된 공고");
    }
    @Test void searchEvidenceChangesOnlyAfterApproval() {
        var published = publish();
        var evidence = new CompanyJobContent.Input("수정 공고", "backend", "신입", "서울", "정규직", "Java", "업무와 자격", LocalDate.now().plusDays(10), "https://example.com/jobs", LocalDate.now().minusDays(2), true);
        var edited = service.edit("owner", company.getId(), published.id(), published.version(), evidence);
        var live = posts.findById(published.publishedJobId()).orElseThrow();
        assertThat(live.getOriginalPostedDate()).isNull();
        assertThat(live.isCompleteDescriptionConfirmed()).isFalse();
        var submitted = service.submit("owner", company.getId(), edited.id(), edited.version());
        service.review(submitted.id(), submitted.version(), "APPROVE", "확인", ADMIN);
        live = posts.findById(published.publishedJobId()).orElseThrow();
        assertThat(live.getOriginalPostedDate()).isEqualTo(LocalDate.now().minusDays(2));
        assertThat(live.isCompleteDescriptionConfirmed()).isTrue();
    }
    @Test void closeImmediatelyRemovesPublicVisibility() {
        var p=publish(); var closed=service.close("owner",company.getId(),p.id(),p.version());
        assertThat(closed.liveStatus()).isEqualTo("CLOSED");
        assertThat(posts.findActiveAndNotExpiredSlice(LocalDate.now(),PageRequest.of(0,100)).getContent())
                .noneMatch(j -> j.getId().equals(p.publishedJobId()));
    }
    @Test void suspendedJobCannotBeEditedToBypassModeration() {
        var p=publish(); var suspended=service.review(p.id(),p.version(),"SUSPEND","게시 중단",ADMIN);
        assertThat(suspended.liveStatus()).isEqualTo("HIDDEN");
        assertThatThrownBy(() -> service.edit("owner",company.getId(),p.id(),suspended.version(),input("우회"))).hasMessageContaining("JOB_STATE_CONFLICT");
    }
    @Test void adminHistoryRetainsApprovalAfterHideCloseAndSoftDelete() {
        var p=publish();
        var hidden=service.review(p.id(),p.version(),"SUSPEND","허위 내용 조사",ADMIN);
        var closed=service.review(p.id(),hidden.version(),"FORCE_CLOSE","채용 종료 확인",ADMIN);
        assertThat(closed.liveStatus()).isEqualTo("CLOSED");
        var deleted=service.review(p.id(),closed.version(),"DELETE","기업 삭제 요청",ADMIN);
        assertThat(deleted.state()).isEqualTo(CompanyJob.State.DELETED);
        assertThat(deleted.liveStatus()).isEqualTo("HIDDEN");
        assertThat(posts.findById(p.publishedJobId())).isPresent();
        assertThat(posts.findActiveAndNotExpiredSlice(LocalDate.now(),PageRequest.of(0,100)).getContent())
                .noneMatch(j -> j.getId().equals(p.publishedJobId()));
        for (String action : List.of("APPROVE", "SUSPEND", "FORCE_CLOSE", "DELETE")) {
            var history=service.reviewHistory(0,action,p.id());
            assertThat(history.getTotalElements()).isEqualTo(1);
            var entry=history.getContent().getFirst();
            assertThat(entry.actorId()).isEqualTo(ADMIN.id());
            assertThat(entry.actorType()).isEqualTo("ADMIN");
            assertThat(entry.createdAt()).isNotNull();
            assertThat(entry.reason()).isNotBlank();
            assertThat(entry.companyJobId()).isEqualTo(p.id());
        }
        assertThat(service.reviewDetail(p.id()).history()).hasSize(6);
        assertThatThrownBy(() -> service.edit("owner",company.getId(),p.id(),deleted.version(),input("복구 시도"))).hasMessageContaining("JOB_STATE_CONFLICT");
        assertThatThrownBy(() -> service.close("owner",company.getId(),p.id(),deleted.version())).hasMessageContaining("JOB_STATE_CONFLICT");
        assertThatThrownBy(() -> service.submit("owner",company.getId(),p.id(),deleted.version())).hasMessageContaining("JOB_STATE_CONFLICT");
        assertThatThrownBy(() -> service.review(p.id(),deleted.version(),"APPROVE","복구 시도",ADMIN)).hasMessageContaining("JOB_STATE_CONFLICT");
        assertThatThrownBy(() -> service.review(p.id(),deleted.version(),"DELETE","중복",ADMIN)).hasMessageContaining("JOB_STATE_CONFLICT");
    }
    @Test void historyIsPaginatedNewestFirstAndCanFilterByJob() {
        var d=create(); var other=create();
        for(int i=0;i<25;i++) audits.save(new CompanyJobAudit(d.id(),ADMIN.id(),"ADMIN","REQUEST_CHANGES","보완 "+i));
        audits.flush();
        var first=service.reviewHistory(0,"REQUEST_CHANGES",d.id());
        var second=service.reviewHistory(1,"REQUEST_CHANGES",d.id());
        assertThat(first.getTotalElements()).isEqualTo(25);
        assertThat(first.getContent()).hasSize(20);
        assertThat(second.getContent()).hasSize(5);
        assertThat(first.getContent().getFirst().reason()).isEqualTo("보완 24");
        assertThat(second.getContent().getLast().reason()).isEqualTo("보완 0");
        assertThat(service.reviewHistory(0,"REQUEST_CHANGES",other.id()).getTotalElements()).isZero();
        assertThatThrownBy(() -> service.reviewHistory(0,"UNKNOWN",null)).hasMessageContaining("INVALID_ACTION");
        assertThatThrownBy(() -> service.reviewHistory(-1,null,null)).hasMessageContaining("INVALID_PAGE");
    }
    @Test void moderationRequiresRoleReasonAndCurrentVersion() {
        var p=publish();
        var reviewer=new AdminPrincipal(2L,"r@example.com","Reviewer",AdminRole.REVIEWER,Set.of(),"s","c");
        for(String action : List.of("SUSPEND","FORCE_CLOSE","DELETE")) {
            assertThatThrownBy(() -> service.review(p.id(),p.version(),action,"조회자",reviewer)).hasMessageContaining("ADMIN_ROLE_DENIED");
            assertThatThrownBy(() -> service.review(p.id(),p.version(),action," ",ADMIN)).hasMessageContaining("INVALID_REASON");
            assertThatThrownBy(() -> service.review(p.id(),p.version()+1,action,"버전 충돌",ADMIN)).hasMessageContaining("JOB_VERSION_CONFLICT");
        }
        assertThat(service.reviewDetail(p.id()).history()).hasSize(3);
        assertThat(service.reviewDetail(p.id()).job().liveStatus()).isEqualTo("ACTIVE");
    }
    @Test void forceCloseImmediatelyRemovesActivePublicPost() {
        var p=publish();
        var linked=service.reviewByPublishedId(p.publishedJobId());
        assertThat(linked.id()).isEqualTo(p.id());
        assertThat(linked.version()).isEqualTo(p.version());
        var closed=service.review(linked.id(),linked.version(),"FORCE_CLOSE","채용 종료",ADMIN);
        assertThat(closed.state()).isEqualTo(CompanyJob.State.CLOSED);
        assertThat(closed.liveStatus()).isEqualTo("CLOSED");
        assertThat(posts.findById(p.publishedJobId()).orElseThrow().isActive()).isFalse();
        assertThat(service.reviewByPublishedId(p.publishedJobId()).state()).isEqualTo(CompanyJob.State.CLOSED);
        assertThatThrownBy(() -> service.reviewByPublishedId(-1L)).hasMessageContaining("COMPANY_JOB_NOT_FOUND");
        assertThatThrownBy(() -> service.review(p.id(),closed.version(),"FORCE_CLOSE","중복",ADMIN)).hasMessageContaining("JOB_STATE_CONFLICT");
    }
    @Test void foreignCompanyJobAndStaleVersionAreRejected() {
        var p=create();
        Company other=companies.save(Company.pendingVerification("다른기업","다른기업","a".repeat(64),"123-**-12345",null,user));
        other.changeStatus(CompanyStatus.VERIFIED);
        CompanyMember membership=CompanyMember.invited(other,user,CompanyMemberRole.OWNER,user);
        membership.changeStatus(CompanyMemberStatus.ACTIVE,LocalDateTime.now()); members.saveAndFlush(membership);
        assertThatThrownBy(() -> service.edit("owner",other.getId(),p.id(),p.version(),input("침범"))).hasMessageContaining("COMPANY_JOB_NOT_FOUND");
        assertThatThrownBy(() -> service.submit("owner",company.getId(),p.id(),p.version()+1)).hasMessageContaining("JOB_VERSION_CONFLICT");
    }
    @Test void viewerCannotCreate() {
        UserAccount viewer=users.save(UserAccount.activeSocial("viewer@example.com","viewer",AuthProvider.GITHUB,"viewer"));
        CompanyMember m=CompanyMember.invited(company,viewer,CompanyMemberRole.VIEWER,user);
        m.changeStatus(CompanyMemberStatus.ACTIVE,LocalDateTime.now()); members.saveAndFlush(m);
        when(current.requireCurrentMember("owner")).thenReturn(viewer);
        assertThatThrownBy(this::create).isInstanceOf(CompanyAuthorizationException.class);
    }
    @Test void unverifiedCompanyCannotCreate() {
        company.changeStatus(CompanyStatus.PENDING_VERIFICATION); companies.flush();
        assertThatThrownBy(this::create).hasMessage("COMPANY_NOT_VERIFIED");
    }
    @Test void reviewerCannotApprove() {
        var d=create(); var s=service.submit("owner",company.getId(),d.id(),d.version());
        var reviewer=new AdminPrincipal(2L,"r@example.com","Reviewer",AdminRole.REVIEWER,Set.of(),"s","c");
        assertThatThrownBy(() -> service.review(d.id(),s.version(),"APPROVE","확인",reviewer)).hasMessageContaining("ADMIN_ROLE_DENIED");
    }
    @Test void repeatedApprovalDoesNotCreateSecondPublicPost() {
        var p=publish();
        assertThatThrownBy(() -> service.review(p.id(),p.version(),"APPROVE","재시도",ADMIN)).hasMessageContaining("JOB_STATE_CONFLICT");
        assertThat(posts.findBySourcePlatformAndOriginalSn(SourcePlatform.COMPANY_DIRECT,"company-job-"+p.id())).isPresent();
    }
    @Test void expiryIsExcludedAndEngagementIsIdempotent() {
        var p=publish(); UUID id=UUID.randomUUID();
        var event=new CompanyJobEngagementController.Event(id,CompanyJobEngagementController.Kind.VIEW);
        engagement.record(p.publishedJobId(),event); engagement.record(p.publishedJobId(),event);
        assertThat(service.reviewDetail(p.id()).job().views()).isEqualTo(1);
        engagement.record(p.publishedJobId(),new CompanyJobEngagementController.Event(UUID.randomUUID(),CompanyJobEngagementController.Kind.APPLY_CLICK));
        assertThat(service.reviewDetail(p.id()).job().applyClicks()).isEqualTo(1);
        JobPost live=posts.findById(p.publishedJobId()).orElseThrow();
        live.refreshFromSource("검증기업","마감공고","backend","신입","서울","정규직",LocalDate.now().minusDays(2),LocalDate.now().minusDays(1),"https://example.com/jobs","내용",null);
        posts.flush();
        assertThat(service.reviewDetail(p.id()).job().liveStatus()).isEqualTo("CLOSED");
        service.expire(p.id()); service.expire(p.id());
        assertThat(audits.findTop50ByCompanyJobIdOrderByIdDesc(p.id()).stream().filter(a -> a.getAction().equals("EXPIRED"))).hasSize(1);
        assertThatThrownBy(() -> engagement.record(p.publishedJobId(),event)).hasMessageContaining("404");
    }
}
