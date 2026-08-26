package kr.itsdev.devjobcollector.career;

import java.util.List;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.dto.career.JobBookmarkResponse;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobBookmarkService {
    private final CurrentMemberService currentMemberService;
    private final JobPostRepository jobPostRepository;
    private final JobBookmarkRepository bookmarkRepository;

    public JobBookmarkService(
            CurrentMemberService currentMemberService,
            JobPostRepository jobPostRepository,
            JobBookmarkRepository bookmarkRepository
    ) {
        this.currentMemberService = currentMemberService;
        this.jobPostRepository = jobPostRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    @Transactional
    public JobBookmarkResponse create(String subject, Long jobPostId) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        JobPost jobPost = jobPostRepository.findById(jobPostId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job post not found"));

        bookmarkRepository.insertIfAbsent(member.getId(), jobPost.getId());
        JobBookmark bookmark = bookmarkRepository.findByOwnerAndJobForUpdate(member.getId(), jobPost.getId())
                .orElseThrow(() -> new IllegalStateException("Bookmark insert did not produce a row"));
        return JobBookmarkResponse.from(bookmark);
    }

    @Transactional(readOnly = true)
    public List<JobBookmarkResponse> list(String subject) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        return bookmarkRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(member.getId()).stream()
                .map(JobBookmarkResponse::from)
                .toList();
    }

    @Transactional
    public void delete(String subject, Long jobPostId) {
        UserAccount member = currentMemberService.requireCurrentMember(subject);
        bookmarkRepository.deleteByUser_IdAndJobPost_Id(member.getId(), jobPostId);
    }
}
