package kr.itsdev.devjobcollector.dto.company;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.company.Company;
import kr.itsdev.devjobcollector.company.CompanyMember;
import kr.itsdev.devjobcollector.company.CompanyMemberRole;
import kr.itsdev.devjobcollector.company.CompanyMemberStatus;
import kr.itsdev.devjobcollector.company.CompanyStatus;
import kr.itsdev.devjobcollector.company.CompanyVerificationRequest;
import kr.itsdev.devjobcollector.company.CompanyVerificationStatus;

public record CompanySummaryResponse(
        Long companyId,
        String legalName,
        String displayName,
        String businessNumberMasked,
        String websiteUrl,
        CompanyStatus companyStatus,
        Long membershipId,
        CompanyMemberRole role,
        CompanyMemberStatus membershipStatus,
        Long verificationRequestId,
        CompanyVerificationStatus verificationStatus,
        LocalDateTime verificationRequestedAt,
        LocalDateTime verificationReviewedAt
) {
    public static CompanySummaryResponse from(
            CompanyMember membership,
            CompanyVerificationRequest latestVerification
    ) {
        Company company = membership.getCompany();
        return new CompanySummaryResponse(
                company.getId(),
                company.getLegalName(),
                company.getDisplayName(),
                company.getBusinessNumberMasked(),
                company.getWebsiteUrl(),
                company.getStatus(),
                membership.getId(),
                membership.getRole(),
                membership.getStatus(),
                latestVerification == null ? null : latestVerification.getId(),
                latestVerification == null ? null : latestVerification.getStatus(),
                latestVerification == null ? null : latestVerification.getRequestedAt(),
                latestVerification == null ? null : latestVerification.getReviewedAt()
        );
    }
}
