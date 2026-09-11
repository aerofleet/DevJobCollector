package kr.itsdev.devjobcollector.company;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupResponse;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.hardening.SecurityAction;
import kr.itsdev.devjobcollector.security.hardening.SecurityAuditEventType;
import kr.itsdev.devjobcollector.security.hardening.SecurityHardeningService;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanySignupFacade {
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;
    private final CurrentMemberService currentMemberService;
    private final SecurityHardeningService hardeningService;
    private final Clock clock = Clock.systemDefaultZone();

    public CompanySignupFacade(CompanyRepository companyRepository,
                               CompanyMemberRepository memberRepository,
                               CurrentMemberService currentMemberService,
                               SecurityHardeningService hardeningService) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.currentMemberService = currentMemberService;
        this.hardeningService = hardeningService;
    }

    @Transactional
    public CompanySignupResponse signup(String subject, CompanySignupRequest request) {
        UserAccount owner = currentMemberService.requireCurrentMember(subject);
        var protectedNumber = BusinessNumberProtection.protect(request.businessNumber());
        hardeningService.checkRateLimit(SecurityAction.COMPANY_SIGNUP,
                "actor:" + owner.getId(), "business:" + protectedNumber.hash());
        if (companyRepository.existsByBusinessNumberHash(protectedNumber.hash())) {
            throw new CompanyAlreadyExistsException();
        }

        Company company;
        try {
            company = companyRepository.saveAndFlush(Company.pendingVerification(
                    request.legalName(), request.displayName(), protectedNumber.hash(),
                    protectedNumber.masked(), request.websiteUrl(), owner));
        } catch (DataIntegrityViolationException conflict) {
            throw new CompanyAlreadyExistsException(conflict);
        }

        CompanyMember membership = memberRepository.saveAndFlush(CompanyMember.activeOwner(
                company, owner, LocalDateTime.now(clock)));
        hardeningService.audit(SecurityAuditEventType.COMPANY_CREATED, owner.getId(),
                owner.getId(), company.getId(), null, CompanyStatus.PENDING_VERIFICATION.name());
        return new CompanySignupResponse(
                company.getId(), company.getStatus(), membership.getId(),
                membership.getRole(), membership.getStatus());
    }
}
