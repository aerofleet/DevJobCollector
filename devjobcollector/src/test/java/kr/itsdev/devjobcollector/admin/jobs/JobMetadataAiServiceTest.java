package kr.itsdev.devjobcollector.admin.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import kr.itsdev.devjobcollector.admin.AdminRole;
import kr.itsdev.devjobcollector.admin.auth.AdminPrincipal;
import kr.itsdev.devjobcollector.ai.OpenAiEvidenceClient;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class JobMetadataAiServiceTest {
    private final JobPostRepository jobs = mock(JobPostRepository.class);
    private final OpenAiEvidenceClient ai = mock(OpenAiEvidenceClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminPrincipal admin = new AdminPrincipal(1L, "admin@example.com", "admin", AdminRole.ADMIN, Set.of(), "session", "csrf");
    private final AdminPrincipal reviewer = new AdminPrincipal(2L, "reviewer@example.com", "reviewer", AdminRole.REVIEWER, Set.of(), "session", "csrf");
    private JobPost job;

    @BeforeEach void setup() {
        job = mock(JobPost.class);
        when(job.getId()).thenReturn(42L);
        when(job.getVersion()).thenReturn(3L);
        when(job.getOriginalSn()).thenReturn("permitted:123");
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.GREENHOUSE);
        when(job.getApplyQual()).thenReturn("<p>자격요건</p><p>개발 경력 3년 이상</p><p>근무지 서울</p>");
        when(jobs.findById(42L)).thenReturn(Optional.of(job));
        when(ai.available()).thenReturn(true);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }
    private JobMetadataAiService service(boolean enabled, String allowed) {
        return new JobMetadataAiService(jobs, ai, jdbc, enabled, allowed);
    }

    @Test void deniesUnapprovedSourceWithoutSendingAnything() {
        var service = service(true, "GREENHOUSE:other");
        var preview = service.preview(42, admin);
        assertThat(preview.sourceAllowed()).isFalse();
        assertThatThrownBy(() -> service.analyze(42, preview.inputHash(), true, admin)).isInstanceOf(ResponseStatusException.class);
        verify(ai, never()).suggestJobMetadata(anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void reportsTruncatedPreviewWithoutSplittingUnicode() {
        when(job.getApplyQual()).thenReturn("a".repeat(11999) + "😀" + "b".repeat(5));
        var preview = service(true, "GREENHOUSE:permitted").preview(42, admin);
        assertThat(preview.truncated()).isTrue();
        assertThat(preview.content()).hasSize(11999);
    }

    @Test void doesNotSendJobsWhoseExperienceAndLocationAreKnown() {
        when(job.getExperienceCategory()).thenReturn("EXPERIENCED");
        when(job.getLocation()).thenReturn("서울");
        var service = service(true, "GREENHOUSE:permitted");
        var preview = service.preview(42, admin);
        assertThatThrownBy(() -> service.analyze(42, preview.inputHash(), true, admin)).isInstanceOf(ResponseStatusException.class);
        verify(ai, never()).suggestJobMetadata(anyString());
    }

    @Test void requiresEnabledFeatureConfirmationAndMatchingPreview() {
        var service = service(false, "GREENHOUSE:permitted");
        var preview = service.preview(42, admin);
        assertThatThrownBy(() -> service.analyze(42, preview.inputHash(), true, admin)).isInstanceOf(ResponseStatusException.class);
        var enabled = service(true, "GREENHOUSE:permitted");
        assertThatThrownBy(() -> enabled.analyze(42, preview.inputHash(), false, admin)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> enabled.analyze(42, "0".repeat(64), true, admin)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> enabled.preview(42, reviewer)).isInstanceOf(ResponseStatusException.class);
        verify(ai, never()).suggestJobMetadata(anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void rejectsInventedQuoteAndNeverStoresIt() throws Exception {
        var service = service(true, "GREENHOUSE:permitted");
        var preview = service.preview(42, admin);
        when(ai.suggestJobMetadata(anyString())).thenReturn(new ObjectMapper().readTree("""
                {"experience":"경력 5년","experienceQuote":"개발 경력 5년 이상","location":"서울","locationQuote":"근무지 서울"}
                """));
        assertThatThrownBy(() -> service.analyze(42, preview.inputHash(), true, admin)).isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void savesGroundedSuggestionsForReviewWithoutChangingJob() throws Exception {
        var service = service(true, "GREENHOUSE:permitted");
        var preview = service.preview(42, admin);
        when(ai.suggestJobMetadata(anyString())).thenReturn(new ObjectMapper().readTree("""
                {"experience":"경력 3년 이상","experienceQuote":"개발 경력 3년 이상","location":"서울","locationQuote":"근무지 서울"}
                """));
        assertThat(service.analyze(42, preview.inputHash(), true, admin)).containsEntry("status", "NEEDS_REVIEW");
        verify(jdbc).update(anyString(), any(Object[].class));
        verify(jobs, never()).save(any());
    }

    @Test void discardsResultIfPostingChangesDuringAnalysis() throws Exception {
        var service = service(true, "GREENHOUSE:permitted");
        var preview = service.preview(42, admin);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(ai.suggestJobMetadata(anyString())).thenReturn(new ObjectMapper().readTree("""
                    {"experience":"경력 3년 이상","experienceQuote":"개발 경력 3년 이상","location":"","locationQuote":""}
                    """));
        assertThatThrownBy(() -> service.analyze(42, preview.inputHash(), true, admin)).isInstanceOf(ResponseStatusException.class);
        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
