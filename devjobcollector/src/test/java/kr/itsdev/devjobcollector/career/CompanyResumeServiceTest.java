package kr.itsdev.devjobcollector.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import kr.itsdev.devjobcollector.company.*;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyResumeServiceTest {
    @Test
    void requiresCompanyPermissionBeforeAnyResumeLookup() {
        CurrentMemberService members = mock(CurrentMemberService.class);
        CompanyAuthorizationService authorization = mock(CompanyAuthorizationService.class);
        CareerResumeRepository resumes = mock(CareerResumeRepository.class);
        UserAccount member = mock(UserAccount.class);
        when(member.getId()).thenReturn(42L);
        when(members.requireCurrentMember("42")).thenReturn(member);
        when(authorization.authorize(7L, 42L, CompanyPermission.VIEW_RESUMES))
                .thenThrow(CompanyAuthorizationException.accessDenied());
        CompanyResumeService service = new CompanyResumeService(members, authorization, resumes, new ObjectMapper());
        assertThatThrownBy(() -> service.list("42", 7L, 0)).isInstanceOf(CompanyAuthorizationException.class);
        assertThatThrownBy(() -> service.get("42", 7L, 11L)).isInstanceOf(CompanyAuthorizationException.class);
        verifyNoInteractions(resumes);
    }

    @Test
    void detailUsesCompanyScopedLookupAndHidesInaccessibleResume() {
        CurrentMemberService members = mock(CurrentMemberService.class);
        CompanyAuthorizationService authorization = mock(CompanyAuthorizationService.class);
        CareerResumeRepository resumes = mock(CareerResumeRepository.class);
        UserAccount member = mock(UserAccount.class);
        when(member.getId()).thenReturn(42L);
        when(members.requireCurrentMember("42")).thenReturn(member);
        when(resumes.findVisibleToCompanyById(7L, 11L)).thenReturn(Optional.empty());
        CompanyResumeService service = new CompanyResumeService(members, authorization, resumes, new ObjectMapper());
        assertThatThrownBy(() -> service.get("42", 7L, 11L)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
        CareerResume resume = CareerResume.draft(member, "지원자", "{\"basicInfo\":{\"name\":\"지원자\"}}");
        when(resumes.findVisibleToCompanyById(7L, 11L)).thenReturn(Optional.of(resume));
        assertThat(service.get("42", 7L, 11L).content().path("basicInfo").path("name").asText()).isEqualTo("지원자");
        verify(resumes, never()).findById(any());
    }
}
