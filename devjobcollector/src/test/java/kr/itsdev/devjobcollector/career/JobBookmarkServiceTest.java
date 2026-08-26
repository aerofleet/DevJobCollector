package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JobBookmarkServiceTest {
    private CurrentMemberService currentMemberService;
    private JobPostRepository jobPostRepository;
    private JobBookmarkRepository bookmarkRepository;
    private JobBookmarkService service;

    @BeforeEach
    void setUp() {
        currentMemberService = mock(CurrentMemberService.class);
        jobPostRepository = mock(JobPostRepository.class);
        bookmarkRepository = mock(JobBookmarkRepository.class);
        service = new JobBookmarkService(currentMemberService, jobPostRepository, bookmarkRepository);
    }

    @Test
    void createsBookmarkIdempotentlyForAuthenticatedOwner() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        JobBookmark bookmark = JobBookmark.create(member, jobPost);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(jobPostRepository.findById(7L)).thenReturn(Optional.of(jobPost));
        when(bookmarkRepository.findByOwnerAndJobForUpdate(42L, 7L)).thenReturn(Optional.of(bookmark));

        var response = service.create("42", 7L);

        assertThat(response.jobPostId()).isEqualTo(7L);
        assertThat(response.companyName()).isEqualTo("테스트 기업");
        verify(bookmarkRepository).insertIfAbsent(42L, 7L);
    }

    @Test
    void listsOnlyCurrentOwnersBookmarks() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(bookmarkRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(42L))
                .thenReturn(List.of(JobBookmark.create(member, jobPost)));

        assertThat(service.list("42"))
                .extracting(response -> response.jobPostId())
                .containsExactly(7L);
        verify(bookmarkRepository).findAllByUser_IdOrderByCreatedAtDescIdDesc(42L);
    }

    @Test
    void deletesOnlyByCurrentOwnerAndJobPair() {
        UserAccount member = member(42L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);

        service.delete("42", 7L);

        verify(bookmarkRepository).deleteByUser_IdAndJobPost_Id(42L, 7L);
    }

    @Test
    void returnsNotFoundWithoutWritingForUnknownJob() {
        UserAccount member = member(42L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(jobPostRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("42", 404L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private UserAccount member(Long id) {
        UserAccount member = mock(UserAccount.class);
        when(member.getId()).thenReturn(id);
        return member;
    }

    private JobPost jobPost(Long id) {
        JobPost jobPost = mock(JobPost.class);
        when(jobPost.getId()).thenReturn(id);
        when(jobPost.getCompanyName()).thenReturn("테스트 기업");
        when(jobPost.getTitle()).thenReturn("백엔드 개발자");
        when(jobPost.getEndDate()).thenReturn(LocalDate.of(2026, 9, 30));
        when(jobPost.getOriginalUrl()).thenReturn("https://example.com/jobs/" + id);
        return jobPost;
    }
}
