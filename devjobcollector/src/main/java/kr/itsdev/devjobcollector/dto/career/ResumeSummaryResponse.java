package kr.itsdev.devjobcollector.dto.career;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.career.CareerResume;
import kr.itsdev.devjobcollector.career.ResumeStatus;

public record ResumeSummaryResponse(
        Long id,
        String title,
        ResumeStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ResumeSummaryResponse from(CareerResume resume) {
        return new ResumeSummaryResponse(
                resume.getId(),
                resume.getTitle(),
                resume.getStatus(),
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}
