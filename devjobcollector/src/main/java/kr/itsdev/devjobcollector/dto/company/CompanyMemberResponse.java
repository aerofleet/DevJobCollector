package kr.itsdev.devjobcollector.dto.company;

import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.company.CompanyMember;
import kr.itsdev.devjobcollector.company.CompanyMemberRole;
import kr.itsdev.devjobcollector.company.CompanyMemberStatus;

public record CompanyMemberResponse(
        Long memberId,
        Long companyId,
        Long userId,
        String email,
        String name,
        CompanyMemberRole role,
        CompanyMemberStatus status,
        LocalDateTime joinedAt
) {
    public static CompanyMemberResponse from(CompanyMember member) {
        return new CompanyMemberResponse(
                member.getId(), member.getCompany().getId(), member.getUser().getId(),
                member.getUser().getEmail(), member.getUser().getName(),
                member.getRole(), member.getStatus(), member.getJoinedAt());
    }
}
