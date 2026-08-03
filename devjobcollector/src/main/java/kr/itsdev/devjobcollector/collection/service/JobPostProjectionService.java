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
        String originalSn = truncate(target.getSourceIdentifier() + ":" + rawJob.sourceJobId(), 255);
        LocalDate startDate = rawJob.publishedAt() == null
                ? LocalDate.now(SERVICE_ZONE)
                : rawJob.publishedAt().atZone(SERVICE_ZONE).toLocalDate();
        String sourceUrl = firstNonBlank(rawJob.sourceUrl(), rawJob.applyUrl());
        if (sourceUrl == null || rawJob.title() == null || rawJob.title().isBlank()) {
            throw new IllegalArgumentException("ATS job requires title and source URL: " + originalSn);
        }

        JobPost jobPost = jobPostRepository.findBySourcePlatformAndOriginalSn(platform, originalSn)
                .orElseGet(() -> JobPost.builder()
                        .sourcePlatform(platform)
                        .originalSn(originalSn)
                        .companyName(truncate(target.displayCompanyName(), 150))
                        .title(truncate(rawJob.title(), 255))
                        .jobCategory(truncate(rawJob.team(), 255))
                        .experience("경력무관")
                        .location(truncate(rawJob.location(), 255))
                        .hireType(truncate(rawJob.employmentType(), 255))
                        .startDate(startDate)
                        .endDate(OPEN_ENDED_DATE)
                        .originalUrl(sourceUrl)
                        .applyQual(rawJob.plainDescription())
                        .processInfo(null)
                        .build());

        jobPost.refreshFromSource(
                truncate(target.displayCompanyName(), 150),
                truncate(rawJob.title(), 255),
                truncate(rawJob.team(), 255),
                "경력무관",
                truncate(rawJob.location(), 255),
                truncate(rawJob.employmentType(), 255),
                startDate,
                OPEN_ENDED_DATE,
                sourceUrl,
                rawJob.plainDescription(),
                null);
        return jobPostRepository.save(jobPost);
    }

    private static SourcePlatform toPlatform(SourceType sourceType) {
        return switch (sourceType) {
            case GREENHOUSE -> SourcePlatform.GREENHOUSE;
            case LEVER -> SourcePlatform.LEVER;
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
