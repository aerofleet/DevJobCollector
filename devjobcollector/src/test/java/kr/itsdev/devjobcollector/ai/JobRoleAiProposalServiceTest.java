package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JobRoleAiProposalServiceTest {
    private static final String TITLE = "Manager, Selection Operations (Product Lifecycle Selection)";
    private static final String CONTENT = "Run the drop process end to end. Lead and coach operators.";
    private final OpenAiEvidenceClient client = mock(OpenAiEvidenceClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void disabledByDefaultDoesNotCallProvider() {
        var service = new JobRoleAiProposalService(client, false);
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", TITLE, CONTENT, "v1", "gpt-5.4-mini");
        assertThatThrownBy(() -> service.propose(input)).hasMessage("JOB_ROLE_AI_DISABLED");
        verify(client, never()).suggestJobRole(TITLE, CONTENT);
    }

    @Test void acceptsEvidenceBackedProposalButDoesNotPublish() throws Exception {
        when(client.available()).thenReturn(true);
        when(client.suggestJobRole(TITLE, CONTENT)).thenReturn(mapper.readTree("""
                {"mode":"NEW","role":"상품 운영 매니저","evidence":"Lead and coach operators."}
                """));
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", TITLE, CONTENT, "v1", "gpt-5.4-mini");
        var result = new JobRoleAiProposalService(client, true).propose(input);
        assertThat(result.role()).isEqualTo("상품 운영 매니저");
        assertThat(result.inputHash()).isEqualTo(input.inputHash());
    }

    @Test void rejectsProviderEvidenceAbsentFromThisJob() throws Exception {
        when(client.available()).thenReturn(true);
        when(client.suggestJobRole(TITLE, CONTENT)).thenReturn(mapper.readTree("""
                {"mode":"NEW","role":"상품 운영 매니저","evidence":"invented evidence"}
                """));
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", TITLE, CONTENT, "v1", "gpt-5.4-mini");
        assertThatThrownBy(() -> new JobRoleAiProposalService(client, true).propose(input))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("INVALID_ROLE_EVIDENCE");
    }
}
