package kr.itsdev.devjobcollector.admin.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.admin.AdminAuditLogRepository;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import kr.itsdev.devjobcollector.service.StripeSalaryEvidenceService;
import kr.itsdev.devjobcollector.service.StripeSalaryPageClient;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripeSalaryApplyServiceTest {
    @Test void appliesOnlyMatchingVersionAndSourceWithAudit() {
        var jobs = mock(JobPostRepository.class); var pages = mock(StripeSalaryPageClient.class);
        var evidence = mock(StripeSalaryEvidenceService.class); var audits = mock(AdminAuditLogRepository.class);
        var actor = new AdminPrincipal(1L, "admin@example.com", "Admin", AdminRole.ADMIN, Set.of(), "s", "h");
        var job = JobPost.builder().sourcePlatform(SourcePlatform.GREENHOUSE).originalSn("stripe:8330801")
                .companyName("Stripe").title("Role").startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2030, 1, 1)).originalUrl("https://stripe.com/jobs/search?gh_jid=8330801").build();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(jobs.findByIdForModeration(1L)).thenReturn(Optional.of(job));
        when(pages.fetch("8330801")).thenReturn(Optional.of("page"));
        when(evidence.preview("8330801", "page")).thenReturn(new StripeSalaryEvidenceService.Preview("PRESENT", "a".repeat(64), List.of("Salary A$10,000")));
        var service = new StripeSalaryApplyService(jobs, pages, evidence, audits, new ObjectMapper());
        assertThatThrownBy(() -> service.apply(1, 0, "b".repeat(64), actor, "request" )).hasMessageContaining("SOURCE_CHANGED");
        assertThat(job.getSupplementalSalaryText()).isNull();
        verifyNoInteractions(audits);
        assertThat(service.apply(1, 0, "a".repeat(64), actor, "request")).containsEntry("status", "APPLIED");
        assertThat(job.getSupplementalSalaryText()).isEqualTo("Salary A$10,000");
        verify(audits).save(any());
    }
}
