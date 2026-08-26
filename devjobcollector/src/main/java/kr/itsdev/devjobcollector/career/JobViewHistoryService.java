package kr.itsdev.devjobcollector.career;

import java.time.LocalDateTime;
import java.util.List;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.dto.career.JobViewHistoryResponse;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobViewHistoryService {
    public static final int RETENTION_LIMIT = 100;

    private final CurrentMemberService currentMemberService;
    private final JobPostRepository jobPostRepository;
    private final JobViewHistoryRepository viewHistoryRepository;

    public JobViewHistoryService(
            CurrentMemberService currentMemberService,
            JobPostRepository jobPostRepository,
            JobViewHistoryRepository viewHistoryRepository
    ) {
        this.currentMemberService = currentMemberService;
        this.jobPostRepository = jobPostRepository;
        this.viewHistoryRepository = viewHistoryRepository;
    }

    @Transactional
    public JobViewHistoryResponse record(String subject, Long jobPostId) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        JobPost jobPost = jobPostRepository.findById(jobPostId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job post not found"));

        viewHistoryRepository.lockUserById(member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member not found"));
        viewHistoryRepository.recordView(member.getId(), jobPost.getId(), LocalDateTime.now());
        pruneOldest(member.getId());

        JobViewHistory history = viewHistoryRepository
                .findByUser_IdAndJobPost_Id(member.getId(), jobPost.getId())
                .orElseThrow(() -> new IllegalStateException("View history upsert did not produce a row"));
        return JobViewHistoryResponse.from(history);
    }

    @Transactional(readOnly = true)
    public List<JobViewHistoryResponse> list(String subject) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        return viewHistoryRepository
                .findTop100ByUser_IdOrderByLastViewedAtDescIdDesc(member.getId()).stream()
                .map(JobViewHistoryResponse::from)
                .toList();
    }

    private void pruneOldest(Long userId) {
        List<Long> ids = viewHistoryRepository.findIdsByUserIdOrderByMostRecent(userId);
        if (ids.size() > RETENTION_LIMIT) {
            viewHistoryRepository.deleteAllByIdInBatch(ids.subList(RETENTION_LIMIT, ids.size()));
        }
    }
}
