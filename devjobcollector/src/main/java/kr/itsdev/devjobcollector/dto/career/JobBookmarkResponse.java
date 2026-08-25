package kr.itsdev.devjobcollector.dto.career;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.career.JobBookmark;
import kr.itsdev.devjobcollector.domain.JobPost;

public record JobBookmarkResponse(
        Long bookmarkId,
        Long jobPostId,
        String companyName,
        String title,
        String location,
        String experience,
        LocalDate endDate,
        String originalUrl,
        LocalDateTime bookmarkedAt
) {
    public static JobBookmarkResponse from(JobBookmark bookmark) {
        JobPost jobPost = bookmark.getJobPost();
        return new JobBookmarkResponse(
                bookmark.getId(),
                jobPost.getId(),
                jobPost.getCompanyName(),
                jobPost.getTitle(),
                jobPost.getLocation(),
                jobPost.getExperience(),
                jobPost.getEndDate(),
                jobPost.getOriginalUrl(),
                bookmark.getCreatedAt()
        );
    }
}
