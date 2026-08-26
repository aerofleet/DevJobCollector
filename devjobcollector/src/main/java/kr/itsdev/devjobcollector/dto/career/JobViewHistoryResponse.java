package kr.itsdev.devjobcollector.dto.career;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.career.JobViewHistory;
import kr.itsdev.devjobcollector.domain.JobPost;

public record JobViewHistoryResponse(
        Long viewHistoryId,
        Long jobPostId,
        String companyName,
        String title,
        String location,
        String experience,
        LocalDate endDate,
        String originalUrl,
        LocalDateTime firstViewedAt,
        LocalDateTime lastViewedAt,
        int viewCount
) {
    public static JobViewHistoryResponse from(JobViewHistory history) {
        JobPost jobPost = history.getJobPost();
        return new JobViewHistoryResponse(
                history.getId(),
                jobPost.getId(),
                jobPost.getCompanyName(),
                jobPost.getTitle(),
                jobPost.getLocation(),
                jobPost.getExperience(),
                jobPost.getEndDate(),
                jobPost.getOriginalUrl(),
                history.getFirstViewedAt(),
                history.getLastViewedAt(),
                history.getViewCount()
        );
    }
}
