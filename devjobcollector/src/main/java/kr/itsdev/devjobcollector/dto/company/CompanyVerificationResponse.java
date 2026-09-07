package kr.itsdev.devjobcollector.dto.company;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequest;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;

public record CompanyVerificationResponse(
        Long requestId,
        Long companyId,
        CompanyVerificationStatus requestStatus,
        CompanyStatus companyStatus,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt
) {
    public static CompanyVerificationResponse from(CompanyVerificationRequest request) {
        return new CompanyVerificationResponse(
                request.getId(),
                request.getCompany().getId(),
                request.getStatus(),
                request.getCompany().getStatus(),
                request.getRequestedAt(),
                request.getReviewedAt()
        );
    }
}
