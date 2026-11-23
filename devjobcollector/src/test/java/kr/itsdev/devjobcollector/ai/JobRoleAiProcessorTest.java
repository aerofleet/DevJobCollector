package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.SourcePlatform;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import org.junit.jupiter.api.Test;

class JobRoleAiProcessorTest {
    private final JobPostRepository jobs = mock(JobPostRepository.class);
    private final OpenAiEvidenceClient client = mock(OpenAiEvidenceClient.class);
    private final JobRoleAiProposalService proposals = mock(JobRoleAiProposalService.class);
    private final JobRoleAiProposalStore store = mock(JobRoleAiProposalStore.class);
    private final JobRoleAiBudget budget = mock(JobRoleAiBudget.class);
    private final JobRoleAiCatalog catalog = mock(JobRoleAiCatalog.class);

    private JobPost job(long version) {
        var job = mock(JobPost.class);
        when(job.getId()).thenReturn(13758L);
        when(job.getVersion()).thenReturn(version);
        when(job.getTitle()).thenReturn("Selection Operations Manager");
        when(job.getApplyQual()).thenReturn("Run selection operations and lead a team.");
        when(job.getSourcePlatform()).thenReturn(SourcePlatform.GREENHOUSE);
        when(job.getOriginalSn()).thenReturn("coupang:13758");
        when(job.getModerationStatus()).thenReturn(JobModerationStatus.ACTIVE);
        when(job.isActive()).thenReturn(true);
        when(job.getEndDate()).thenReturn(LocalDate.now().plusDays(1));
        return job;
    }

    private JobRoleAiProcessor processor(boolean enabled, String sources) {
        when(catalog.allowedRoles()).thenReturn(JobRoleAiRules.EXISTING_ROLES);
        return new JobRoleAiProcessor(jobs, client, proposals, store, budget, catalog, enabled, sources);
    }

    @Test void disabledOrUnapprovedSourceDoesNotCallModel() {
        assertThat(processor(false, "GREENHOUSE").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.DISABLED);
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        assertThat(processor(true, "").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.SOURCE_NOT_ALLOWED);
        assertThat(processor(true, "GREENHOUSE").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.SOURCE_NOT_ALLOWED);
        assertThat(processor(true, "GREENHOUSE:stripe").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.SOURCE_NOT_ALLOWED);
        verify(client, never()).countJobRoleInputTokens(anyString(), anyString(), anyString(), any());
    }

    @Test void publicAlioUsesExplicitPublicSourceKey() {
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(current.getSourcePlatform()).thenReturn(SourcePlatform.PUBLIC_ALIO);
        when(current.getOriginalSn()).thenReturn("12345");
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        when(client.countJobRoleInputTokens(anyString(), anyString(), anyString(), any())).thenReturn(1000);
        when(budget.reserve(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.DAILY_LIMIT);
        assertThat(processor(true, "PUBLIC_ALIO:PUBLIC_ALIO").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.DAILY_LIMIT);
        verify(proposals, never()).propose(any());
    }

    @Test void oversizedInputIsDistinguishedFromMissingSource() {
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        when(client.countJobRoleInputTokens(anyString(), anyString(), anyString(), any())).thenReturn(2001);
        assertThat(processor(true, "GREENHOUSE:coupang").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.INPUT_TOO_LARGE);
        verify(proposals, never()).propose(any());
    }

    @Test void reservesBudgetBeforeGeneratingAndStoresOnlyAProposal() {
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        when(client.countJobRoleInputTokens(anyString(), anyString(), anyString(), any())).thenReturn(1000);
        when(budget.reserve(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.RESERVED);
        when(proposals.propose(any())).thenAnswer(call -> {
            JobRoleAiInput.Prepared input = call.getArgument(0);
            return new JobRoleAiProposalService.Proposal(input.inputHash(), "NEW", "상품 운영 매니저", "Run selection operations");
        });
        when(store.save(anyLong(), anyLong(), anyString(), any(), any())).thenReturn(JobRoleAiProposalStore.SaveResult.STORED);
        assertThat(processor(true, "GREENHOUSE:coupang").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.STORED);
        var order = org.mockito.Mockito.inOrder(budget, proposals, store);
        order.verify(budget).reserve(anyLong(), anyString(), anyInt());
        order.verify(proposals).propose(any());
        order.verify(store).save(anyLong(), anyLong(), anyString(), any(), any());
    }

    @Test void staleJobOrSpentBudgetDoesNotGenerate() {
        when(client.available()).thenReturn(true);
        when(client.countJobRoleInputTokens(anyString(), anyString(), anyString(), any())).thenReturn(1000);
        JobPost original = job(0);
        JobPost updated = job(1);
        when(jobs.findById(13758L)).thenReturn(Optional.of(original), Optional.of(updated));
        assertThat(processor(true, "GREENHOUSE:coupang").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.JOB_CHANGED);
        verify(budget, never()).reserve(anyLong(), anyString(), anyInt());
        verify(proposals, never()).propose(any());
    }

    @Test void spentBudgetPreventsGeneration() {
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        when(client.countJobRoleInputTokens(anyString(), anyString(), anyString(), any())).thenReturn(1000);
        when(budget.reserve(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.DAILY_LIMIT);
        assertThat(processor(true, "GREENHOUSE:coupang").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.DAILY_LIMIT);
        verify(proposals, never()).propose(any());
        verify(store, never()).save(anyLong(), anyLong(), anyString(), any(), any());
    }

    @Test void repeatedInputDoesNotSendEvenATokenCountRequest() {
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        when(budget.alreadyClaimed(anyLong(), anyString())).thenReturn(true);
        assertThat(processor(true, "GREENHOUSE:coupang").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.ALREADY_CLAIMED);
        verify(client, never()).countJobRoleInputTokens(anyString(), anyString(), anyString(), any());
        verify(proposals, never()).propose(any());
    }

    @Test void failedGenerationCanRetryWithoutRefundingTheReservation() {
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        when(client.countJobRoleInputTokens(anyString(), anyString(), anyString(), any())).thenReturn(1000);
        when(budget.reserve(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.RESERVED);
        when(proposals.propose(any())).thenThrow(new IllegalStateException("provider failed"));

        assertThatThrownBy(() -> processor(true, "GREENHOUSE:coupang").process(13758))
                .isInstanceOf(IllegalStateException.class);
        var order = org.mockito.Mockito.inOrder(budget, proposals);
        order.verify(budget).reserve(anyLong(), anyString(), anyInt());
        order.verify(proposals).propose(any());
        order.verify(budget).releaseFailedClaim(anyLong(), anyString());
        verify(store, never()).save(anyLong(), anyLong(), anyString(), any(), any());
    }

    @Test void overBudgetInputUsesCompleteDelimitedSectionsAndReservesTheirTokenCount() {
        when(client.available()).thenReturn(true);
        JobPost current = job(0);
        when(current.getApplyQual()).thenReturn("회사 소개\n소개 문구\n업무 내용\n상품 규제 검토.\n자격 요건\n운영 경력 3년.\n우대 사항\n영어.");
        when(jobs.findById(13758L)).thenReturn(Optional.of(current));
        when(client.countJobRoleInputTokens(anyString(), anyString(), anyString(), any())).thenReturn(2100, 1000);
        when(budget.reserve(anyLong(), anyString(), anyInt())).thenReturn(JobRoleAiBudget.Reservation.RESERVED);
        when(proposals.propose(any())).thenAnswer(call -> {
            JobRoleAiInput.Prepared input = call.getArgument(0);
            assertThat(input.content()).contains("상품 규제 검토.", "운영 경력 3년.").doesNotContain("소개 문구", "영어.");
            return new JobRoleAiProposalService.Proposal(input.inputHash(), "NEW", "상품 운영", "상품 규제 검토.");
        });
        when(store.save(anyLong(), anyLong(), anyString(), any(), any())).thenReturn(JobRoleAiProposalStore.SaveResult.STORED);
        assertThat(processor(true, "GREENHOUSE:coupang").process(13758)).isEqualTo(JobRoleAiProcessor.Outcome.STORED);
        verify(budget).reserve(anyLong(), anyString(), eq(1000));
    }
}
