package kr.itsdev.devjobcollector.collection.service;

import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class JobPostProjectionService {

    static final LocalDate OPEN_ENDED_DATE = LocalDate.of(9999, 12, 31);
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final JobPostRepository jobPostRepository;

    public JobPost upsert(CompanySourceTarget target, JobRawDto rawJob) {
        SourcePlatform platform = toPlatform(rawJob.provider());
        if (rawJob.sourceJobId() == null || rawJob.sourceJobId().isBlank()) {
            throw new IllegalArgumentException("ATS job requires source job ID");
        }
        String originalSn = truncate(rawJob.provider() == SourceType.SARAMIN
                ? rawJob.sourceJobId()
                : target.getSourceIdentifier() + ":" + rawJob.sourceJobId(), 255);
        LocalDate startDate = rawJob.publishedAt() == null
                ? LocalDate.now(SERVICE_ZONE)
                : rawJob.publishedAt().atZone(SERVICE_ZONE).toLocalDate();
        LocalDate sourceEndDate = rawJob.deadlineAt() == null
                ? OPEN_ENDED_DATE
                : rawJob.deadlineAt().atZone(SERVICE_ZONE).toLocalDate();
        LocalDate endDate = sourceEndDate.isBefore(startDate) ? OPEN_ENDED_DATE : sourceEndDate;
        String companyName = firstNonBlank(rawJob.companyName(), target.displayCompanyName());
        String experience = firstNonBlank(rawJob.experience(), "경력무관");
        String sourceUrl = firstNonBlank(rawJob.sourceUrl(), rawJob.applyUrl());
        if (sourceUrl == null || rawJob.title() == null || rawJob.title().isBlank()) {
            throw new IllegalArgumentException("ATS job requires title and source URL: " + originalSn);
        }

        JobPost jobPost = jobPostRepository.findBySourcePlatformAndOriginalSn(platform, originalSn)
                .orElseGet(() -> JobPost.builder()
                        .sourcePlatform(platform)
                        .originalSn(originalSn)
                        .companyName(truncate(companyName, 150))
                        .title(truncate(rawJob.title(), 255))
                        .jobCategory(truncate(rawJob.team(), 255))
                        .experience(truncate(experience, 255))
                        .location(truncate(rawJob.location(), 255))
                        .hireType(truncate(rawJob.employmentType(), 255))
                        .startDate(startDate)
                        .endDate(endDate)
                        .originalUrl(sourceUrl)
                        .applyQual(rawJob.plainDescription())
                        .processInfo(null)
                        .build());

        jobPost.refreshFromSource(
                truncate(companyName, 150),
                truncate(rawJob.title(), 255),
                truncate(rawJob.team(), 255),
                truncate(experience, 255),
                truncate(rawJob.location(), 255),
                truncate(rawJob.employmentType(), 255),
                startDate,
                endDate,
                sourceUrl,
                rawJob.plainDescription(),
                null);
        return jobPostRepository.save(jobPost);
    }

    private static SourcePlatform toPlatform(SourceType sourceType) {
        return switch (sourceType) {
            case GREENHOUSE -> SourcePlatform.GREENHOUSE;
            case LEVER -> SourcePlatform.LEVER;
            case SARAMIN -> SourcePlatform.SARAMIN;
            case TOSS_CAREERS -> SourcePlatform.COMPANY_PAGE;
            case COMPANY_PAGE -> SourcePlatform.COMPANY_PAGE;
            default -> throw new IllegalArgumentException("Unsupported ATS provider: " + sourceType);
        };
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
