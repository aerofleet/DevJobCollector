package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JobViewHistoryServiceTest {
    private CurrentMemberService currentMemberService;
    private JobPostRepository jobPostRepository;
    private JobViewHistoryRepository viewHistoryRepository;
    private JobViewHistoryService service;

    @BeforeEach
    void setUp() {
        currentMemberService = mock(CurrentMemberService.class);
        jobPostRepository = mock(JobPostRepository.class);
        viewHistoryRepository = mock(JobViewHistoryRepository.class);
        service = new JobViewHistoryService(currentMemberService, jobPostRepository, viewHistoryRepository);
    }

    @Test
    void recordsViewForAuthenticatedOwner() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        JobViewHistory history = JobViewHistory.firstView(
                member, jobPost, LocalDateTime.of(2026, 8, 26, 10, 0));
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(jobPostRepository.findById(7L)).thenReturn(Optional.of(jobPost));
        when(viewHistoryRepository.lockUserById(42L)).thenReturn(Optional.of(42L));
        when(viewHistoryRepository.findIdsByUserIdOrderByMostRecent(42L)).thenReturn(List.of(11L));
        when(viewHistoryRepository.findByUser_IdAndJobPost_Id(42L, 7L)).thenReturn(Optional.of(history));

        var response = service.record("42", 7L);

        assertThat(response.jobPostId()).isEqualTo(7L);
        assertThat(response.companyName()).isEqualTo("테스트 기업");
        assertThat(response.viewCount()).isEqualTo(1);
        verify(viewHistoryRepository).lockUserById(42L);
        verify(viewHistoryRepository).recordView(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(7L), any(LocalDateTime.class));
        verify(viewHistoryRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void prunesAllRowsBeyondOneHundredMostRecent() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        JobViewHistory history = JobViewHistory.firstView(member, jobPost, LocalDateTime.now());
        List<Long> orderedIds = LongStream.rangeClosed(1, 103).boxed().toList();
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(jobPostRepository.findById(7L)).thenReturn(Optional.of(jobPost));
        when(viewHistoryRepository.lockUserById(42L)).thenReturn(Optional.of(42L));
        when(viewHistoryRepository.findIdsByUserIdOrderByMostRecent(42L)).thenReturn(orderedIds);
        when(viewHistoryRepository.findByUser_IdAndJobPost_Id(42L, 7L)).thenReturn(Optional.of(history));

        service.record("42", 7L);

        verify(viewHistoryRepository).deleteAllByIdInBatch(List.of(101L, 102L, 103L));
    }

    @Test
    void listsOnlyCurrentOwnersMostRecentHistory() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(viewHistoryRepository.findTop100ByUser_IdOrderByLastViewedAtDescIdDesc(42L))
                .thenReturn(List.of(JobViewHistory.firstView(member, jobPost, LocalDateTime.now())));

        assertThat(service.list("42"))
                .extracting(response -> response.jobPostId())
                .containsExactly(7L);
        verify(viewHistoryRepository).findTop100ByUser_IdOrderByLastViewedAtDescIdDesc(42L);
    }

    @Test
    void returnsNotFoundWithoutWritingForUnknownJob() {
        UserAccount member = member(42L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(jobPostRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record("42", 404L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        verify(viewHistoryRepository, never()).recordView(any(), any(), any());
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
        when(jobPost.getLocation()).thenReturn("서울");
        when(jobPost.getExperience()).thenReturn("신입");
        when(jobPost.getEndDate()).thenReturn(LocalDate.of(2026, 9, 30));
        when(jobPost.getOriginalUrl()).thenReturn("https://example.com/jobs/" + id);
        return jobPost;
    }
}
