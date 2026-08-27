package kr.itsdev.devjobcollector.dto.career;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.career.CareerResume;
import kr.itsdev.devjobcollector.career.ResumeStatus;

public record ResumeDetailResponse(
        Long id,
        String title,
        ResumeStatus status,
        JsonNode content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ResumeDetailResponse from(CareerResume resume, JsonNode content) {
        return new ResumeDetailResponse(
                resume.getId(),
                resume.getTitle(),
                resume.getStatus(),
                content,
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}
