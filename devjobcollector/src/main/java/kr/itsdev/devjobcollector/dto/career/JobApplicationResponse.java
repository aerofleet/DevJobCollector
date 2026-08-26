package kr.itsdev.devjobcollector.dto.career;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.career.ApplicationStatus;
import kr.itsdev.devjobcollector.career.JobApplication;
import kr.itsdev.devjobcollector.domain.JobPost;

public record JobApplicationResponse(
        Long applicationId,
        Long jobPostId,
        String companyName,
        String title,
        String location,
        String experience,
        LocalDate endDate,
        String originalUrl,
        ApplicationStatus status,
        LocalDateTime appliedAt,
        String memo,
        LocalDateTime updatedAt
) {
    public static JobApplicationResponse from(JobApplication application) {
        JobPost jobPost = application.getJobPost();
        return new JobApplicationResponse(
                application.getId(), jobPost.getId(), jobPost.getCompanyName(), jobPost.getTitle(),
                jobPost.getLocation(), jobPost.getExperience(), jobPost.getEndDate(), jobPost.getOriginalUrl(),
                application.getStatus(), application.getAppliedAt(), application.getMemo(), application.getUpdatedAt()
        );
    }
}
