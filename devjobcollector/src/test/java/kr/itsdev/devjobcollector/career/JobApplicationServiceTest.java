package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.dto.career.JobApplicationCreateRequest;
import kr.itsdev.devjobcollector.dto.career.JobApplicationStatusUpdateRequest;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JobApplicationServiceTest {
    private CurrentMemberService currentMemberService;
    private JobPostRepository jobPostRepository;
    private JobApplicationRepository applicationRepository;
    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        currentMemberService = mock(CurrentMemberService.class);
        jobPostRepository = mock(JobPostRepository.class);
        applicationRepository = mock(JobApplicationRepository.class);
        service = new JobApplicationService(currentMemberService, jobPostRepository, applicationRepository);
    }

    @Test
    void createsAppliedApplicationForAuthenticatedOwner() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        JobApplication application = JobApplication.applied(member, jobPost, LocalDateTime.now(), "서류 제출");
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(jobPostRepository.findById(7L)).thenReturn(Optional.of(jobPost));
        when(applicationRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);
        when(applicationRepository.findByUser_IdAndJobPost_Id(42L, 7L)).thenReturn(Optional.of(application));

        var response = service.create("42", 7L, new JobApplicationCreateRequest("  서류 제출  "));

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(response.memo()).isEqualTo("서류 제출");
        verify(applicationRepository).insertIfAbsent(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.eq(7L),
                any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq("서류 제출"));
    }

    @Test
    void returnsExistingApplicationWithoutOverwritingOnDuplicateCreate() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        JobApplication existing = JobApplication.applied(member, jobPost, LocalDateTime.now(), "기존 메모");
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(jobPostRepository.findById(7L)).thenReturn(Optional.of(jobPost));
        when(applicationRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);
        when(applicationRepository.findByUser_IdAndJobPost_Id(42L, 7L)).thenReturn(Optional.of(existing));

        var response = service.create("42", 7L, new JobApplicationCreateRequest("새 메모"));

        assertThat(response.memo()).isEqualTo("기존 메모");
        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void listsOnlyCurrentOwnersApplications() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(applicationRepository.findAllByUser_IdOrderByUpdatedAtDescIdDesc(42L))
                .thenReturn(List.of(JobApplication.applied(member, jobPost, LocalDateTime.now(), null)));

        assertThat(service.list("42")).extracting(response -> response.jobPostId()).containsExactly(7L);
    }

    @Test
    void changesStatusOnlyThroughOwnerScopedApplicationId() {
        UserAccount member = member(42L);
        JobPost jobPost = jobPost(7L);
        JobApplication application = JobApplication.applied(member, jobPost, LocalDateTime.now(), null);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(applicationRepository.findByIdAndUser_Id(11L, 42L)).thenReturn(Optional.of(application));

        var response = service.changeStatus(
                "42", 11L, new JobApplicationStatusUpdateRequest(ApplicationStatus.INTERVIEW));

        assertThat(response.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        verify(applicationRepository).findByIdAndUser_Id(11L, 42L);
    }

    @Test
    void hidesAnotherOwnersApplicationAsNotFound() {
        UserAccount member = member(42L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
        when(applicationRepository.findByIdAndUser_Id(99L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(
                "42", 99L, new JobApplicationStatusUpdateRequest(ApplicationStatus.REJECTED)))
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
        when(jobPost.getLocation()).thenReturn("서울");
        when(jobPost.getExperience()).thenReturn("신입");
        when(jobPost.getEndDate()).thenReturn(LocalDate.of(2026, 9, 30));
        when(jobPost.getOriginalUrl()).thenReturn("https://example.com/jobs/" + id);
        return jobPost;
    }
}
