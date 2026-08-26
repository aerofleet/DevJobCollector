package kr.itsdev.devjobcollector.career;

import java.time.LocalDateTime;
import java.util.List;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.dto.career.JobApplicationCreateRequest;
import kr.itsdev.devjobcollector.dto.career.JobApplicationResponse;
import kr.itsdev.devjobcollector.dto.career.JobApplicationStatusUpdateRequest;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobApplicationService {
    private final CurrentMemberService currentMemberService;
    private final JobPostRepository jobPostRepository;
    private final JobApplicationRepository applicationRepository;

    public JobApplicationService(
            CurrentMemberService currentMemberService,
            JobPostRepository jobPostRepository,
            JobApplicationRepository applicationRepository
    ) {
        this.currentMemberService = currentMemberService;
        this.jobPostRepository = jobPostRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public JobApplicationResponse create(
            String subject, Long jobPostId, JobApplicationCreateRequest request
    ) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        applicationRepository.lockUserById(member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member not found"));
        JobPost jobPost = jobPostRepository.findById(jobPostId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job post not found"));
        String memo = normalizeMemo(request == null ? null : request.memo());

        applicationRepository.insertIfAbsent(member.getId(), jobPost.getId(), LocalDateTime.now(), memo);
        JobApplication application = applicationRepository
                .findByOwnerAndJobForUpdate(member.getId(), jobPost.getId())
                .orElseThrow(() -> new IllegalStateException("Application insert did not produce a row"));
        return JobApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> list(String subject) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        return applicationRepository.findAllByUser_IdOrderByUpdatedAtDescIdDesc(member.getId()).stream()
                .map(JobApplicationResponse::from)
                .toList();
    }

    @Transactional
    public JobApplicationResponse changeStatus(
            String subject, Long applicationId, JobApplicationStatusUpdateRequest request
    ) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application status is required");
        }
        JobApplication application = applicationRepository.findByIdAndUser_Id(applicationId, member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        application.changeStatus(request.status());
        return JobApplicationResponse.from(application);
    }

    private String normalizeMemo(String memo) {
        try {
            return JobApplication.normalizeMemo(memo);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }
}
