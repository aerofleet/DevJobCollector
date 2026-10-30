package kr.itsdev.devjobcollector.admin.jobs;

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

class StripeSalaryPreviewServiceTest {
    private static AdminPrincipal actor(AdminRole role) { return new AdminPrincipal(1L, "admin@example.com", "Admin", role, Set.of(), "s", "h"); }

    @Test void previewShowsSourceAndEvidenceWithoutWriting() {
        var jobs = mock(JobPostRepository.class);
        var pages = mock(StripeSalaryPageClient.class);
        var evidence = mock(StripeSalaryEvidenceService.class);
        var job = JobPost.builder().sourcePlatform(SourcePlatform.GREENHOUSE).originalSn("stripe:8330801")
                .companyName("Stripe").title("Role").startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2030, 1, 1)).originalUrl("https://stripe.com/jobs/search?gh_jid=8330801").build();
        when(jobs.findById(11051L)).thenReturn(Optional.of(job));
        when(pages.fetch("8330801")).thenReturn(Optional.of("page"));
        when(evidence.preview("8330801", "page")).thenReturn(new StripeSalaryEvidenceService.Preview("PRESENT", "hash", List.of("OTE salary")));
        var service = new StripeSalaryPreviewService(jobs, pages, evidence);
        var preview = service.preview(11051, actor(AdminRole.ADMIN));
        assertThat(preview.sourceId()).isEqualTo("8330801");
        assertThat(preview.previousText()).isNull();
        assertThat(preview.paragraphs()).containsExactly("OTE salary");
        assertThat(preview.sourceHash()).isEqualTo("hash");
        verify(jobs, never()).save(any());
    }

    @Test void reviewerAndUnsupportedSourcesCannotFetch() {
        var jobs = mock(JobPostRepository.class);
        var pages = mock(StripeSalaryPageClient.class);
        var evidence = mock(StripeSalaryEvidenceService.class);
        var service = new StripeSalaryPreviewService(jobs, pages, evidence);
        assertThatThrownBy(() -> service.preview(1, actor(AdminRole.REVIEWER))).hasMessageContaining("ADMIN_ROLE_DENIED");
        var job = JobPost.builder().sourcePlatform(SourcePlatform.LEVER).originalSn("stripe:8330801")
                .companyName("Stripe").title("Role").startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2030, 1, 1)).originalUrl("https://example.com").build();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        assertThatThrownBy(() -> service.preview(1, actor(AdminRole.ADMIN))).hasMessageContaining("UNSUPPORTED_SOURCE");
        verifyNoInteractions(pages, evidence);
    }
}
