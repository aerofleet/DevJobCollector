package kr.itsdev.devjobcollector.dto.company;

import kr.itsdev.devjobcollector.company.CompanyMemberRole;
import kr.itsdev.devjobcollector.company.CompanyMemberStatus;
import kr.itsdev.devjobcollector.company.CompanyStatus;

public record CompanySignupResponse(
        Long companyId,
        CompanyStatus companyStatus,
        Long membershipId,
        CompanyMemberRole role,
        CompanyMemberStatus membershipStatus
) {
}
