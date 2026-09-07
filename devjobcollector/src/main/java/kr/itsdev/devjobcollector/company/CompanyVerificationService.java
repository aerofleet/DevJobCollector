package kr.itsdev.devjobcollector.company;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationResponse;
import kr.itsdev.devjobcollector.dto.company.CompanyVerificationSubmitRequest;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyVerificationService {
    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;
    private final CompanyVerificationRequestRepository requestRepository;
    private final CurrentMemberService currentMemberService;
    private final Clock clock;

    @Autowired
    public CompanyVerificationService(CompanyRepository companyRepository,
                                      CompanyMemberRepository memberRepository,
                                      CompanyVerificationRequestRepository requestRepository,
                                      CurrentMemberService currentMemberService) {
        this(companyRepository, memberRepository, requestRepository,
                currentMemberService, Clock.systemDefaultZone());
    }

    CompanyVerificationService(CompanyRepository companyRepository,
                               CompanyMemberRepository memberRepository,
                               CompanyVerificationRequestRepository requestRepository,
                               CurrentMemberService currentMemberService,
                               Clock clock) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.currentMemberService = currentMemberService;
        this.clock = clock;
    }

    @Transactional
    public CompanyVerificationResponse submit(String subject, Long companyId,
                                              CompanyVerificationSubmitRequest request) {
        UserAccount requester = currentMemberService.requireCurrentMember(subject);
        Company company = companyRepository.findByIdForUpdate(companyId)
                .orElseThrow(CompanyVerificationException::companyNotFound);
        CompanyMember membership = memberRepository
                .findByCompany_IdAndUser_Id(companyId, requester.getId())
                .filter(CompanyMember::isActiveOwner)
                .orElseThrow(CompanyVerificationException::ownerRequired);

        if (company.getStatus() != CompanyStatus.PENDING_VERIFICATION
                && company.getStatus() != CompanyStatus.REJECTED) {
            throw CompanyVerificationException.invalidCompanyStatus();
        }
        if (requestRepository.existsByCompany_IdAndStatus(
                companyId, CompanyVerificationStatus.PENDING)) {
            throw CompanyVerificationException.pendingRequestExists();
        }

        if (company.getStatus() == CompanyStatus.REJECTED) {
            company.changeStatus(CompanyStatus.PENDING_VERIFICATION);
        }
        CompanyVerificationRequest verificationRequest = CompanyVerificationRequest.pending(
                company, membership.getUser(), request.method(), request.evidenceObjectKey(),
                LocalDateTime.now(clock));
        return CompanyVerificationResponse.from(requestRepository.saveAndFlush(verificationRequest));
    }

    @Transactional
    public CompanyVerificationResponse approve(String subject, Long requestId) {
        return review(subject, requestId, null, true);
    }

    @Transactional
    public CompanyVerificationResponse reject(String subject, Long requestId, String reason) {
        return review(subject, requestId, reason, false);
    }

    private CompanyVerificationResponse review(String subject, Long requestId,
                                               String rejectionReason, boolean approve) {
        UserAccount reviewer = requirePlatformAdmin(subject);
        CompanyVerificationRequest snapshot = requestRepository.findById(requestId)
                .orElseThrow(CompanyVerificationException::requestNotFound);
        Company company = companyRepository.findByIdForUpdate(snapshot.getCompany().getId())
                .orElseThrow(CompanyVerificationException::companyNotFound);
        CompanyVerificationRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(CompanyVerificationException::requestNotFound);

        if (request.getStatus() != CompanyVerificationStatus.PENDING) {
            throw CompanyVerificationException.alreadyReviewed();
        }
        if (company.getStatus() != CompanyStatus.PENDING_VERIFICATION) {
            throw CompanyVerificationException.invalidCompanyStatus();
        }
        LocalDateTime reviewedAt = LocalDateTime.now(clock);
        if (approve) {
            request.approve(reviewer, reviewedAt);
            company.changeStatus(CompanyStatus.VERIFIED);
        } else {
            request.reject(reviewer, rejectionReason, reviewedAt);
            company.changeStatus(CompanyStatus.REJECTED);
        }
        requestRepository.flush();
        return CompanyVerificationResponse.from(request);
    }

    private UserAccount requirePlatformAdmin(String subject) {
        UserAccount reviewer = currentMemberService.requireCurrentMember(subject);
        if (!PLATFORM_ADMIN.equals(reviewer.getRole())) {
            throw CompanyVerificationException.platformAdminRequired();
        }
        return reviewer;
    }
}
