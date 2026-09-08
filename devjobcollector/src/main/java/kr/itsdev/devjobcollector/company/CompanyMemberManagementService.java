package kr.itsdev.devjobcollector.company;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberInvitationRequest;
import kr.itsdev.devjobcollector.dto.company.CompanyMemberResponse;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.account.UserAccountRepository;
import kr.itsdev.devjobcollector.security.account.UserAccountStatus;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyMemberManagementService {
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;
    private final UserAccountRepository userRepository;
    private final CurrentMemberService currentMemberService;
    private final CompanyAuthorizationService authorizationService;
    private final Clock clock;

    @Autowired
    public CompanyMemberManagementService(CompanyRepository companyRepository,
                                          CompanyMemberRepository memberRepository,
                                          UserAccountRepository userRepository,
                                          CurrentMemberService currentMemberService,
                                          CompanyAuthorizationService authorizationService) {
        this(companyRepository, memberRepository, userRepository, currentMemberService,
                authorizationService, Clock.systemDefaultZone());
    }

    CompanyMemberManagementService(CompanyRepository companyRepository,
                                   CompanyMemberRepository memberRepository,
                                   UserAccountRepository userRepository,
                                   CurrentMemberService currentMemberService,
                                   CompanyAuthorizationService authorizationService,
                                   Clock clock) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.currentMemberService = currentMemberService;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CompanyMemberResponse> listMembers(String subject, Long companyId) {
        UserAccount actor = currentMemberService.requireCurrentMember(subject);
        authorizationService.authorize(companyId, actor.getId(), CompanyPermission.VIEW_MEMBERS);
        return memberRepository.findAllByCompany_IdAndStatusNotOrderByIdAsc(
                        companyId, CompanyMemberStatus.LEFT).stream()
                .map(CompanyMemberResponse::from)
                .toList();
    }

    @Transactional
    public CompanyMemberResponse invite(String subject, Long companyId,
                                        CompanyMemberInvitationRequest request) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(request.role(), "role is required");
        UserAccount actor = currentMemberService.requireCurrentMember(subject);
        CompanyMember actorMembership = lockActor(companyId, actor.getId());
        authorizationService.authorize(actorMembership, permissionForRole(request.role()));

        UserAccount invitee = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(user -> user.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(CompanyMemberManagementException::inviteeNotFound);
        var existing = memberRepository.findByCompanyAndUserForUpdate(companyId, invitee.getId());
        if (existing.isPresent()) {
            CompanyMember membership = existing.get();
            if (membership.getStatus() != CompanyMemberStatus.LEFT) {
                throw CompanyMemberManagementException.memberAlreadyExists();
            }
            membership.reinvite(request.role(), actor);
            return CompanyMemberResponse.from(membership);
        }

        try {
            CompanyMember membership = memberRepository.saveAndFlush(CompanyMember.invited(
                    actorMembership.getCompany(), invitee, request.role(), actor));
            return CompanyMemberResponse.from(membership);
        } catch (DataIntegrityViolationException conflict) {
            throw CompanyMemberManagementException.memberAlreadyExists();
        }
    }

    @Transactional
    public CompanyMemberResponse changeRole(String subject, Long companyId, Long memberId,
                                            CompanyMemberRole newRole) {
        Objects.requireNonNull(newRole, "newRole is required");
        UserAccount actor = currentMemberService.requireCurrentMember(subject);
        CompanyMember actorMembership = lockActor(companyId, actor.getId());
        authorizationService.authorize(actorMembership, CompanyPermission.INVITE_MEMBERS);
        CompanyMember target = requireMemberForUpdate(companyId, memberId);
        requireRoleChangeable(target);
        authorizationService.authorize(
                actorMembership, permissionForRoleChange(target.getRole(), newRole));

        if (target.isActiveOwner() && newRole != CompanyMemberRole.OWNER) {
            requireAnotherActiveOwner(companyId);
        }
        target.changeRole(newRole);
        return CompanyMemberResponse.from(target);
    }

    @Transactional
    public void remove(String subject, Long companyId, Long memberId) {
        UserAccount actor = currentMemberService.requireCurrentMember(subject);
        CompanyMember actorMembership = lockActor(companyId, actor.getId());
        authorizationService.authorize(actorMembership, CompanyPermission.REMOVE_MEMBER);
        CompanyMember target = requireMemberForUpdate(companyId, memberId);
        if (target.getStatus() == CompanyMemberStatus.LEFT) {
            throw CompanyMemberManagementException.memberInactive();
        }
        if (target.getRole() == CompanyMemberRole.OWNER) {
            authorizationService.authorize(actorMembership, CompanyPermission.TRANSFER_OWNERSHIP);
        }
        if (target.isActiveOwner()) {
            requireAnotherActiveOwner(companyId);
        }
        target.changeStatus(CompanyMemberStatus.LEFT, LocalDateTime.now(clock));
    }

    private CompanyMember lockActor(Long companyId, Long actorId) {
        companyRepository.findByIdForUpdate(companyId)
                .orElseThrow(CompanyMemberManagementException::companyNotFound);
        return memberRepository.findByCompanyAndUserForUpdate(companyId, actorId)
                .orElseThrow(CompanyAuthorizationException::accessDenied);
    }

    private CompanyMember requireMemberForUpdate(Long companyId, Long memberId) {
        return memberRepository.findByIdAndCompanyForUpdate(memberId, companyId)
                .orElseThrow(CompanyMemberManagementException::memberNotFound);
    }

    private void requireRoleChangeable(CompanyMember target) {
        if (target.getStatus() != CompanyMemberStatus.ACTIVE
                && target.getStatus() != CompanyMemberStatus.INVITED) {
            throw CompanyMemberManagementException.memberInactive();
        }
    }

    private void requireAnotherActiveOwner(Long companyId) {
        long activeOwners = memberRepository.countByCompany_IdAndRoleAndStatus(
                companyId, CompanyMemberRole.OWNER, CompanyMemberStatus.ACTIVE);
        if (activeOwners <= 1) {
            throw new LastActiveOwnerException();
        }
    }

    private CompanyPermission permissionForRoleChange(CompanyMemberRole currentRole,
                                                      CompanyMemberRole newRole) {
        if (currentRole == CompanyMemberRole.OWNER || newRole == CompanyMemberRole.OWNER) {
            return CompanyPermission.TRANSFER_OWNERSHIP;
        }
        if (currentRole == CompanyMemberRole.ADMIN || newRole == CompanyMemberRole.ADMIN) {
            return CompanyPermission.ASSIGN_ADMIN;
        }
        return permissionForRole(newRole);
    }

    private CompanyPermission permissionForRole(CompanyMemberRole role) {
        return switch (role) {
            case OWNER -> CompanyPermission.TRANSFER_OWNERSHIP;
            case ADMIN -> CompanyPermission.ASSIGN_ADMIN;
            case RECRUITER -> CompanyPermission.ASSIGN_RECRUITER;
            case VIEWER -> CompanyPermission.INVITE_MEMBERS;
        };
    }
}
