package kr.itsdev.devjobcollector.company;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.dto.company.CompanySignupRequest;
import kr.itsdev.devjobcollector.dto.company.CompanySignupResponse;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanySignupFacade {
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;
    private final CurrentMemberService currentMemberService;
    private final Clock clock = Clock.systemDefaultZone();

    public CompanySignupFacade(CompanyRepository companyRepository,
                               CompanyMemberRepository memberRepository,
                               CurrentMemberService currentMemberService) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.currentMemberService = currentMemberService;
    }

    @Transactional
    public CompanySignupResponse signup(String subject, CompanySignupRequest request) {
        UserAccount owner = currentMemberService.requireCurrentMember(subject);
        var protectedNumber = BusinessNumberProtection.protect(request.businessNumber());
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
        return new CompanySignupResponse(
                company.getId(), company.getStatus(), membership.getId(),
                membership.getRole(), membership.getStatus());
    }
}
