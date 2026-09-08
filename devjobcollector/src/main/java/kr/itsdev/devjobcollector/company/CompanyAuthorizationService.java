package kr.itsdev.devjobcollector.company;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyAuthorizationService {
    private final CompanyMemberRepository memberRepository;

    public CompanyAuthorizationService(CompanyMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public CompanyMember authorize(Long companyId, Long userId, CompanyPermission permission) {
        Objects.requireNonNull(companyId, "companyId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(permission, "permission is required");

        CompanyMember membership = memberRepository.findByCompany_IdAndUser_Id(companyId, userId)
                .orElseThrow(CompanyAuthorizationException::accessDenied);

        if (membership.getStatus() != CompanyMemberStatus.ACTIVE
                || !permission.allows(membership.getRole())) {
            throw CompanyAuthorizationException.accessDenied();
        }
        if (membership.getCompany().getStatus() != CompanyStatus.VERIFIED) {
            throw CompanyAuthorizationException.companyNotVerified();
        }
        return membership;
    }
}
