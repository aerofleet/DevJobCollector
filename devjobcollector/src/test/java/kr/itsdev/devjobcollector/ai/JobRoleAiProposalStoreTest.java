package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class JobRoleAiProposalStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JobRoleAiProposalStore store = new JobRoleAiProposalStore(jdbc, new ObjectMapper());
    private final JobRoleAiInput.Prepared input = JobRoleAiInput.prepare(13758, "GREENHOUSE", "Selection Operations Manager", null,
            "Run selection operations and lead a team.", "v1", "gpt-5.4-mini");
    private final JobRoleAiProposalService.Proposal proposal = new JobRoleAiProposalService.Proposal(
            input.inputHash(), "NEW", "상품 운영 매니저", "Run selection operations");

    @Test void storesOnlyIfVersionMatchesAndTreatsDuplicatesAsIdempotent() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0).thenThrow(new DuplicateKeyException("duplicate"));
        assertThat(store.save(13758, 4, "v1", input, proposal)).isEqualTo(JobRoleAiProposalStore.SaveResult.STORED);
        assertThat(store.save(13758, 4, "v1", input, proposal)).isEqualTo(JobRoleAiProposalStore.SaveResult.JOB_CHANGED);
        assertThat(store.save(13758, 4, "v1", input, proposal)).isEqualTo(JobRoleAiProposalStore.SaveResult.ALREADY_EXISTS);
    }

    @Test void rejectsAnotherJobCatalogOrInventedEvidenceBeforeAnyDatabaseWrite() {
        assertThatThrownBy(() -> store.save(13759, 4, "v1", input, proposal)).hasMessage("INVALID_ROLE_PROPOSAL");
        assertThatThrownBy(() -> store.save(13758, 4, "v2", input, proposal)).hasMessage("INVALID_ROLE_PROPOSAL");
        var invalid = new JobRoleAiProposalService.Proposal(input.inputHash(), "NEW", "상품 운영 매니저", "not in source");
        assertThatThrownBy(() -> store.save(13758, 4, "v1", input, invalid)).hasMessage("INVALID_ROLE_EVIDENCE");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
