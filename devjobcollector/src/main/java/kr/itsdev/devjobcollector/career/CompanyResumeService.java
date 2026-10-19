package kr.itsdev.devjobcollector.career;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.company.CompanyAuthorizationService;
import kr.itsdev.devjobcollector.company.CompanyPermission;
import kr.itsdev.devjobcollector.dto.career.ResumeDetailResponse;
import kr.itsdev.devjobcollector.dto.career.ResumeSummaryResponse;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CompanyResumeService {
    private final CurrentMemberService members;
    private final CompanyAuthorizationService authorization;
    private final CareerResumeRepository resumes;
    private final ObjectMapper mapper;

    public CompanyResumeService(CurrentMemberService members, CompanyAuthorizationService authorization,
            CareerResumeRepository resumes, ObjectMapper mapper) {
        this.members = members;
        this.authorization = authorization;
        this.resumes = resumes;
        this.mapper = mapper;
    }

    public Page<ResumeSummaryResponse> list(String subject, Long companyId, int page) {
        authorize(subject, companyId);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return resumes.findVisibleToCompany(companyId,
                PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "updatedAt", "id")))
                .map(ResumeSummaryResponse::from);
    }

    public ResumeDetailResponse get(String subject, Long companyId, Long resumeId) {
        authorize(subject, companyId);
        CareerResume resume = resumes.findVisibleToCompanyById(companyId, resumeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
        try {
            return ResumeDetailResponse.from(resume, mapper.readTree(resume.getContentJson()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored resume content is invalid", error);
        }
    }

    private void authorize(String subject, Long companyId) {
        authorization.authorize(companyId, members.requireCurrentMember(subject).getId(), CompanyPermission.VIEW_RESUMES);
    }
}
