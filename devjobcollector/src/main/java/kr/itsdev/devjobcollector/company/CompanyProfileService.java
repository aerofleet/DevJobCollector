package kr.itsdev.devjobcollector.company;

import java.util.List;
import kr.itsdev.devjobcollector.dto.company.CompanySummaryResponse;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyProfileService {
    private final CompanyMemberRepository memberRepository;
    private final CompanyVerificationRequestRepository verificationRepository;
    private final CurrentMemberService currentMemberService;

    public CompanyProfileService(
            CompanyMemberRepository memberRepository,
            CompanyVerificationRequestRepository verificationRepository,
            CurrentMemberService currentMemberService
    ) {
        this.memberRepository = memberRepository;
        this.verificationRepository = verificationRepository;
        this.currentMemberService = currentMemberService;
    }

    @Transactional(readOnly = true)
    public List<CompanySummaryResponse> getMyCompanies(String subject) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        return memberRepository.findAllByUser_IdAndStatusNotOrderByCompany_IdAsc(
                        member.getId(), CompanyMemberStatus.LEFT).stream()
                .map(membership -> CompanySummaryResponse.from(
                        membership,
                        verificationRepository
                                .findTopByCompany_IdOrderByRequestedAtDescIdDesc(
                                        membership.getCompany().getId())
                                .orElse(null)))
                .toList();
    }
}
