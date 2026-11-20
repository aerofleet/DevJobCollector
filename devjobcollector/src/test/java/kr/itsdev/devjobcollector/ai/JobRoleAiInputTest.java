package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class JobRoleAiInputTest {
    private static final String MODEL = "gpt-5.4-mini";

    @Test void normalizesPublicBodyAndBindsHashToJobContentAndPolicy() {
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL);
        assertThat(input.content()).contains("Run selection operations.", "Lead and coach operators.");
        assertThat(input.evidenceSource()).contains("Manager, Selection Operations", "Lead and coach operators.");
        assertThat(input.inputHash()).hasSize(64);
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL).inputHash()).isEqualTo(input.inputHash());
        assertThat(JobRoleAiInput.prepare(13759, "GREENHOUSE", "Manager, Selection Operations",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL).inputHash()).isNotEqualTo(input.inputHash());
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations",
                "<p>Changed duties.</p>", "v1", MODEL).inputHash()).isNotEqualTo(input.inputHash());
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v2", MODEL).inputHash()).isNotEqualTo(input.inputHash());
    }

    @Test void declinesMissingOrOverlongContentRatherThanGuessing() {
        assertThatThrownBy(() -> JobRoleAiInput.prepare(1, "GREENHOUSE", "Manager", " ", "v1", MODEL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobRoleAiInput.prepare(1, "GREENHOUSE", "Manager", "x".repeat(4001), "v1", MODEL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobRoleAiInput.prepare(1, "GREENHOUSE", "Manager", "duties", "v1", "gpt-5-nano"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void longJobUsesOnlyClearlyDelimitedDutiesAndRequiredQualifications() {
        String raw = "About us\n" + "Company marketing. ".repeat(250)
                + "\nJob Description\nRun selection operations.\nBasic Qualifications\nFive years in retail operations."
                + "\nPreferred Qualifications\nOptional SQL experience.\nBenefits\nBenefits information.";
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", "Selection Operations Manager", raw, "v1", MODEL);
        assertThat(input.content()).contains("Run selection operations.", "Five years in retail operations.")
                .doesNotContain("Company marketing", "Optional SQL", "Benefits information");
        assertThatThrownBy(() -> JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager",
                "Company marketing. ".repeat(250) + "\nJob Description\nRun operations.", "v1", MODEL))
                .hasMessage("ROLE_CONTENT_UNUSABLE");
    }
}
