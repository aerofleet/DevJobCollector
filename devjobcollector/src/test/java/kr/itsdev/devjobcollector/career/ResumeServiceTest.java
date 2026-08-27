package kr.itsdev.devjobcollector.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import kr.itsdev.devjobcollector.dto.career.ResumeStatusUpdateRequest;
import kr.itsdev.devjobcollector.dto.career.ResumeUpsertRequest;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ResumeServiceTest {
    private CurrentMemberService currentMemberService;
    private CareerResumeRepository resumeRepository;
    private ObjectMapper objectMapper;
    private ResumeService service;
    private UserAccount member;

    @BeforeEach
    void setUp() {
        currentMemberService = mock(CurrentMemberService.class);
        resumeRepository = mock(CareerResumeRepository.class);
        objectMapper = new ObjectMapper();
        service = new ResumeService(currentMemberService, resumeRepository, objectMapper);
        member = mock(UserAccount.class);
        when(member.getId()).thenReturn(42L);
        when(currentMemberService.requireCurrentMember("42")).thenReturn(member);
    }

    @Test
    void createsDraftForAuthenticatedOwnerAndNormalizesMissingSections() throws Exception {
        when(resumeRepository.save(any(CareerResume.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create("42", new ResumeUpsertRequest(
                "  개발자 이력서  ", objectMapper.readTree("{}")));

        ArgumentCaptor<CareerResume> captor = ArgumentCaptor.forClass(CareerResume.class);
        verify(resumeRepository).save(captor.capture());
        CareerResume saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(member);
        assertThat(saved.getTitle()).isEqualTo("개발자 이력서");
        assertThat(saved.getStatus()).isEqualTo(ResumeStatus.DRAFT);
        assertThat(response.content().get("basicInfo").isObject()).isTrue();
        assertThat(response.content().get("techStack").isArray()).isTrue();
        assertThat(response.content().get("projects").isArray()).isTrue();
        assertThat(response.content().get("experience").isArray()).isTrue();
    }

    @Test
    void listsOnlyCurrentOwnersResumes() {
        CareerResume resume = mock(CareerResume.class);
        when(resume.getTitle()).thenReturn("내 이력서");
        when(resume.getStatus()).thenReturn(ResumeStatus.READY);
        when(resumeRepository.findAllByUser_IdOrderByUpdatedAtDescIdDesc(42L))
                .thenReturn(List.of(resume));

        assertThat(service.list("42")).extracting(item -> item.title()).containsExactly("내 이력서");
        verify(resumeRepository).findAllByUser_IdOrderByUpdatedAtDescIdDesc(42L);
    }

    @Test
    void getsUpdatesChangesStatusAndDeletesOnlyOwnerScopedResume() throws Exception {
        CareerResume resume = CareerResume.draft(member, "초안", "{\"basicInfo\":{},\"techStack\":[],\"projects\":[],\"experience\":[]}");
        when(resumeRepository.findByIdAndUser_Id(11L, 42L)).thenReturn(Optional.of(resume));

        assertThat(service.get("42", 11L).title()).isEqualTo("초안");
        var updated = service.update("42", 11L, new ResumeUpsertRequest(
                "수정본", objectMapper.readTree("{\"projects\":[]}")));
        assertThat(updated.title()).isEqualTo("수정본");
        assertThat(service.changeStatus("42", 11L,
                new ResumeStatusUpdateRequest(ResumeStatus.READY)).status()).isEqualTo(ResumeStatus.READY);

        service.delete("42", 11L);
        verify(resumeRepository).delete(resume);
    }

    @Test
    void hidesAnotherOwnersResumeAsNotFound() {
        when(resumeRepository.findByIdAndUser_Id(99L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("42", 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsNonObjectContentAndInvalidSectionTypes() throws Exception {
        assertThatThrownBy(() -> service.create("42", new ResumeUpsertRequest(
                "이력서", objectMapper.readTree("[]"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.create("42", new ResumeUpsertRequest(
                "이력서", objectMapper.readTree("{\"techStack\":{}}"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
