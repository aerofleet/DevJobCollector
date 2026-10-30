package kr.itsdev.devjobcollector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripeSalaryEvidenceServiceTest {
    private static final String HTML = """
            <script type="application/ld+json">{"@type":"JobPosting","identifier":{"value":"8330801"}}</script>
            <div class="careers-listing-details__body"><h2>Pay and benefits</h2><div class="careers-listing-details__body-content">
            <p>Annual salary OTE A$306,500 - A$459,700 for primary location.</p></div></div>
            """;

    @Test void savesOnlyReviewedMatchingEvidenceAndPreservesItOnSourceRefresh() {
        JobPostRepository jobs = mock(JobPostRepository.class);
        JobPost job = JobPost.builder().sourcePlatform(SourcePlatform.GREENHOUSE).originalSn("stripe:8330801")
                .companyName("Stripe").title("Engineer").startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2030, 1, 1)).originalUrl("https://stripe.com/jobs/search?gh_jid=8330801").build();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        var service = new StripeSalaryEvidenceService(jobs, new ObjectMapper());
        var preview = service.preview("8330801", HTML);
        assertThat(preview.status()).isEqualTo("PRESENT");
        assertThatThrownBy(() -> service.save(1, "8330801", HTML, "bad", LocalDateTime.of(2026, 1, 1, 0, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(job.getSupplementalSalaryText()).isNull();
        service.save(1, "8330801", HTML, preview.sourceHash(), LocalDateTime.of(2026, 1, 2, 0, 0));
        assertThat(job.getSupplementalSalaryText()).contains("A$306,500", "primary location");
        assertThatThrownBy(() -> service.save(1, "8330801", HTML, preview.sourceHash(), LocalDateTime.of(2026, 1, 1, 0, 0))).isInstanceOf(IllegalArgumentException.class);
        job.refreshFromSource("Stripe", "Engineer", null, null, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1), "https://stripe.com/jobs/search?gh_jid=8330801", "Description", null);
        assertThat(job.getSupplementalSalaryText()).contains("A$306,500");
        assertThatThrownBy(() -> service.save(1, "8330802", HTML, preview.sourceHash(), LocalDateTime.of(2026, 1, 3, 0, 0))).isInstanceOf(IllegalArgumentException.class);
    }
}
