package kr.itsdev.devjobcollector.company;

import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyMembershipService {
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;

    public CompanyMembershipService(CompanyRepository companyRepository,
                                    CompanyMemberRepository memberRepository) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public CompanyMember changeRole(Long companyId, Long userId, CompanyMemberRole newRole) {
        Objects.requireNonNull(newRole, "newRole is required");
        lockCompany(companyId);
        CompanyMember member = requireMemberForUpdate(companyId, userId);
        if (member.isActiveOwner() && newRole != CompanyMemberRole.OWNER) {
            requireAnotherActiveOwner(companyId);
        }
        member.changeRole(newRole);
        return member;
    }

    @Transactional
    public CompanyMember changeStatus(Long companyId, Long userId, CompanyMemberStatus newStatus,
                                      LocalDateTime occurredAt) {
        Objects.requireNonNull(newStatus, "newStatus is required");
        lockCompany(companyId);
        CompanyMember member = requireMemberForUpdate(companyId, userId);
        if (member.isActiveOwner() && newStatus != CompanyMemberStatus.ACTIVE) {
            requireAnotherActiveOwner(companyId);
        }
        member.changeStatus(newStatus, occurredAt);
        return member;
    }

    private void lockCompany(Long companyId) {
        companyRepository.findByIdForUpdate(Objects.requireNonNull(companyId, "companyId is required"))
                .orElseThrow(() -> new IllegalArgumentException("COMPANY_NOT_FOUND"));
    }

    private CompanyMember requireMemberForUpdate(Long companyId, Long userId) {
        return memberRepository.findByCompanyAndUserForUpdate(
                        companyId, Objects.requireNonNull(userId, "userId is required"))
                .orElseThrow(() -> new IllegalArgumentException("COMPANY_MEMBER_NOT_FOUND"));
    }

    private void requireAnotherActiveOwner(Long companyId) {
        long activeOwners = memberRepository.countByCompany_IdAndRoleAndStatus(
                companyId, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE);
        if (activeOwners <= 1) {
            throw new LastActiveOwnerException();
        }
    }
}
