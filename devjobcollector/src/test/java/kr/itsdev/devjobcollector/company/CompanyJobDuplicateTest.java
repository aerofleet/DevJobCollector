package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.company.jobs.*;
import kr.itsdev.devjobcollector.repository.*;
import kr.itsdev.devjobcollector.security.account.UserAccount;
import kr.itsdev.devjobcollector.security.service.CurrentMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CompanyJobDuplicateTest {
    @Test void createRejectsSameTitleAndApplyUrl() {
        var drafts = mock(CompanyJobRepository.class);
        var companies = mock(CompanyRepository.class);
        var memberships = mock(CompanyMemberRepository.class);
        var current = mock(CurrentMemberService.class);
        var user = mock(UserAccount.class);
        when(user.getId()).thenReturn(3L);
        when(current.requireCurrentMember("member")).thenReturn(user);
        when(companies.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Company.class)));
        when(memberships.findByCompanyAndUserForUpdate(1L, 3L)).thenReturn(Optional.of(mock(CompanyMember.class)));
        when(drafts.duplicate(1L, null, "Developer", "https://example.com/job/1",
                Set.of(CompanyJob.State.CLOSED, CompanyJob.State.DELETED))).thenReturn(true);
        var service = new CompanyJobService(drafts, mock(CompanyJobAuditRepository.class), companies,
                memberships, mock(CompanyAuthorizationService.class), current, mock(JobPostRepository.class),
                mock(TechStackRepository.class), mock(JdbcTemplate.class));
        var input = new CompanyJobContent.Input(" Developer ", "IT", "", "", "", "", "",
                LocalDate.now(), " https://example.com/job/1 ");
        assertThatThrownBy(() -> service.create("member", 1L, input)).hasMessageContaining("DUPLICATE_COMPANY_JOB");
        verify(drafts, never()).saveAndFlush(any());
    }
}
