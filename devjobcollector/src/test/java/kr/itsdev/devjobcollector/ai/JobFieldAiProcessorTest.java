package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobFieldAiProcessorTest {
    private final JobPostRepository jobs = mock(JobPostRepository.class);
    private final OpenAiEvidenceClient client = mock(OpenAiEvidenceClient.class);
    private final JobRoleAiBudget budget = mock(JobRoleAiBudget.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private JobPost alio() {
        var job = mock(JobPost.class);
        when(job.getId()).thenReturn(1L);
        when(job.getVersion()).thenReturn(0L);
        when(job.getTitle()).thenReturn("전산 연구원 채용");
        when(job.getJobCategory()).thenReturn("정보통신");
        when(job.getApplyQual()).thenReturn("시스템 개발 및 연구 업무");
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.PUBLIC_ALIO);
        when(job.getModerationStatus()).thenReturn(JobModerationStatus.ACTIVE);
        when(job.isActive()).thenReturn(true);
        when(job.getEndDate()).thenReturn(LocalDate.now().plusDays(1));
        return job;
    }

    @Test void alio_requires_explicit_source_and_creates_only_review_proposal() throws Exception {
        when(client.available()).thenReturn(true);
        var job = alio();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        var disabledSource = new JobFieldAiProcessor(jobs, client, budget, jdbc, mapper, true, "");
        assertThat(disabledSource.process(1)).isEqualTo(JobFieldAiProcessor.Outcome.SOURCE_NOT_ALLOWED);
        verify(client, never()).suggestJobFields(anyString(), anyString(), anyString());
        when(client.countJobFieldInputTokens(anyString(), anyString(), anyString())).thenReturn(500);
        when(budget.reserveField(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.RESERVED);
        when(client.suggestJobFields(anyString(), anyString(), anyString())).thenReturn(mapper.readTree("""
                {"fields":[{"field":"전산직","evidence":"시스템 개발"}]}
                """));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var processor = new JobFieldAiProcessor(jobs, client, budget, jdbc, mapper, true, "PUBLIC_ALIO:PUBLIC_ALIO");
        assertThat(processor.process(1)).isEqualTo(JobFieldAiProcessor.Outcome.STORED);
        verify(budget).reserveField(eq(1L), anyString(), eq(500));
        verify(jdbc).update(contains("INSERT INTO job_field_ai_proposals"), any(Object[].class));
    }

    @Test void daily_budget_prevents_generation() {
        when(client.available()).thenReturn(true);
        var job = alio();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(client.countJobFieldInputTokens(anyString(), anyString(), anyString())).thenReturn(500);
        when(budget.reserveField(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.DAILY_LIMIT);
        assertThat(new JobFieldAiProcessor(jobs, client, budget, jdbc, mapper, true, "PUBLIC_ALIO:PUBLIC_ALIO").process(1))
                .isEqualTo(JobFieldAiProcessor.Outcome.DAILY_LIMIT);
        verify(client, never()).suggestJobFields(anyString(), anyString(), anyString());
    }

    @Test void batch_uses_its_lower_reservation_limit() {
        when(client.available()).thenReturn(true);
        var job = alio();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(client.countJobFieldInputTokens(anyString(), anyString(), anyString())).thenReturn(500);
        when(budget.reserveBatchField(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.DAILY_LIMIT);
        assertThat(new JobFieldAiProcessor(jobs, client, budget, jdbc, mapper, true, "PUBLIC_ALIO:PUBLIC_ALIO").processBatch(1))
                .isEqualTo(JobFieldAiProcessor.Outcome.DAILY_LIMIT);
        verify(budget, never()).reserveField(anyLong(), anyString(), anyInt());
        verify(client, never()).suggestJobFields(anyString(), anyString(), anyString());
    }

    @Test void invalid_evidence_is_not_saved_and_can_be_retried_within_budget() throws Exception {
        when(client.available()).thenReturn(true);
        var job = alio();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(client.countJobFieldInputTokens(anyString(), anyString(), anyString())).thenReturn(500);
        when(budget.reserveField(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.RESERVED);
        when(client.suggestJobFields(anyString(), anyString(), anyString())).thenReturn(mapper.readTree("""
                {"fields":[{"field":"계약직","evidence":"시스템 개발"}]}
                """));
        assertThat(new JobFieldAiProcessor(jobs, client, budget, jdbc, mapper, true, "PUBLIC_ALIO:PUBLIC_ALIO").process(1))
                .isEqualTo(JobFieldAiProcessor.Outcome.INVALID_PROPOSAL);
        verify(budget).releaseFailedClaim(eq(1L), anyString());
        verify(jdbc, never()).update(contains("INSERT INTO job_field_ai_proposals"), any(Object[].class));
    }

    @Test void keeps_grounded_field_but_drops_contract_without_contract_in_its_quote() throws Exception {
        when(client.available()).thenReturn(true);
        var job = alio();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(client.countJobFieldInputTokens(anyString(), anyString(), anyString())).thenReturn(500);
        when(budget.reserveField(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.RESERVED);
        when(client.suggestJobFields(anyString(), anyString(), anyString())).thenReturn(mapper.readTree("""
                {"fields":[{"field":"전산직","evidence":"시스템 개발"},{"field":"계약직","evidence":"시스템 개발"}]}
                """));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertThat(new JobFieldAiProcessor(jobs, client, budget, jdbc, mapper, true, "PUBLIC_ALIO:PUBLIC_ALIO").process(1))
                .isEqualTo(JobFieldAiProcessor.Outcome.STORED_PARTIAL);
        var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO job_field_ai_proposals"), args.capture());
        assertThat((String) args.getValue()[3]).contains("전산직").doesNotContain("계약직");
        verify(budget, never()).releaseFailedClaim(anyLong(), anyString());
    }
}
