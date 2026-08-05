package kr.itsdev.devjobcollector.collection.service;

import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostProjectionServiceTest {

    private JobPostRepository repository;
    private JobPostProjectionService service;

    @BeforeEach
    void setUp() {
        repository = mock(JobPostRepository.class);
        service = new JobPostProjectionService(repository);
        when(repository.save(any(JobPost.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsGreenhouseJobPostWithOpenEndedDeadline() {
        CompanySourceTarget target = target("GitLab", SourceType.GREENHOUSE, "gitlab");
        JobRawDto rawJob = rawJob(SourceType.GREENHOUSE, "8503792002", "Backend Engineer");
        when(repository.findBySourcePlatformAndOriginalSn(
                SourcePlatform.GREENHOUSE, "gitlab:8503792002")).thenReturn(Optional.empty());

        JobPost result = service.upsert(target, rawJob);

        assertThat(result.getSourcePlatform()).isEqualTo(SourcePlatform.GREENHOUSE);
        assertThat(result.getOriginalSn()).isEqualTo("gitlab:8503792002");
        assertThat(result.getCompanyName()).isEqualTo("GitLab");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.getEndDate()).isEqualTo(JobPostProjectionService.OPEN_ENDED_DATE);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void refreshesAndReactivatesExistingLeverJobPost() {
        CompanySourceTarget target = target("Integrate", SourceType.LEVER, "integrate");
        JobPost existing = JobPost.builder()
                .sourcePlatform(SourcePlatform.LEVER)
                .originalSn("integrate:job-456")
                .companyName("Old")
                .title("Old title")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(JobPostProjectionService.OPEN_ENDED_DATE)
                .originalUrl("https://old.example/jobs/456")
                .build();
        existing.deactivate();
        when(repository.findBySourcePlatformAndOriginalSn(
                SourcePlatform.LEVER, "integrate:job-456")).thenReturn(Optional.of(existing));

        JobPost result = service.upsert(target, rawJob(SourceType.LEVER, "job-456", "Full Stack Engineer"));

        assertThat(result.getCompanyName()).isEqualTo("Integrate");
        assertThat(result.getTitle()).isEqualTo("Full Stack Engineer");
        assertThat(result.isActive()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    void createsSaraminJobWithSourceCompanyExperienceAndDeadline() {
        CompanySourceTarget target = target("사람인 IT개발·데이터", SourceType.SARAMIN, "22");
        JobRawDto rawJob = new JobRawDto(
                SourceType.SARAMIN,
                "12345",
                "테스트회사",
                "백엔드 개발자",
                "서울 전체",
                "정규직",
                "신입·경력",
                "IT개발·데이터",
                "https://www.saramin.co.kr/jobs/12345",
                "https://www.saramin.co.kr/jobs/12345",
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                Instant.parse("2026-08-31T14:59:59Z"),
                "a".repeat(64),
                "{}");
        when(repository.findBySourcePlatformAndOriginalSn(
                SourcePlatform.SARAMIN, "12345")).thenReturn(Optional.empty());

        JobPost result = service.upsert(target, rawJob);

        assertThat(result.getSourcePlatform()).isEqualTo(SourcePlatform.SARAMIN);
        assertThat(result.getOriginalSn()).isEqualTo("12345");
        assertThat(result.getCompanyName()).isEqualTo("테스트회사");
        assertThat(result.getExperience()).isEqualTo("신입·경력");
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void createsOfficialCompanyPageJob() {
        CompanySourceTarget target = target("공식회사", SourceType.COMPANY_PAGE, "official-company");
        JobRawDto rawJob = rawJob(SourceType.COMPANY_PAGE, "backend-1", "백엔드 개발자");
        when(repository.findBySourcePlatformAndOriginalSn(
                SourcePlatform.COMPANY_PAGE, "official-company:backend-1")).thenReturn(Optional.empty());

        JobPost result = service.upsert(target, rawJob);

        assertThat(result.getSourcePlatform()).isEqualTo(SourcePlatform.COMPANY_PAGE);
        assertThat(result.getOriginalSn()).isEqualTo("official-company:backend-1");
        assertThat(result.getCompanyName()).isEqualTo("공식회사");
    }

    @Test
    void projectsTossCareersAsOfficialCompanyPage() {
        CompanySourceTarget target = target("토스 커뮤니티", SourceType.TOSS_CAREERS, "toss");
        JobRawDto rawJob = rawJob(SourceType.TOSS_CAREERS, "6532656003", "Product Owner");
        when(repository.findBySourcePlatformAndOriginalSn(
                SourcePlatform.COMPANY_PAGE, "toss:6532656003")).thenReturn(Optional.empty());

        JobPost result = service.upsert(target, rawJob);

        assertThat(result.getSourcePlatform()).isEqualTo(SourcePlatform.COMPANY_PAGE);
        assertThat(result.getOriginalSn()).isEqualTo("toss:6532656003");
    }

    private static CompanySourceTarget target(String companyName, SourceType provider, String identifier) {
        return new CompanySourceTarget(
                null, companyName, provider, identifier,
                "https://example.com/careers", CollectionTier.A);
    }

    private static JobRawDto rawJob(SourceType provider, String sourceJobId, String title) {
        return new JobRawDto(
                provider,
                sourceJobId,
                title,
                "Remote",
                "Full Time",
                "Engineering",
                "https://example.com/jobs/" + sourceJobId,
                "https://example.com/jobs/" + sourceJobId + "/apply",
                "Build reliable products",
                Instant.parse("2026-07-31T15:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                "a".repeat(64),
                "{}");
    }
}
