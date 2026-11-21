package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JobRoleAiRulesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String SOURCE = "Manager, Selection Operations (Product Lifecycle Selection)\n"
            + "Run the drop process end to end: intake, evidence check, approval, execution. "
            + "Lead and coach operators on throughput, accuracy and turnaround.";

    @Test void acceptsExistingOrNewRoleOnlyWithSourceEvidence() throws Exception {
        var created = JobRoleAiRules.validate(mapper.readTree("""
                {"mode":"NEW","role":"상품 운영 매니저","evidence":"Lead and coach operators on throughput, accuracy and turnaround."}
                """), SOURCE);
        assertThat(created.role()).isEqualTo("상품 운영 매니저");
        var known = JobRoleAiRules.validate(mapper.readTree("""
                {"mode":"EXISTING","role":"인사 운영","evidence":"Manager, Selection Operations"}
                """), SOURCE);
        assertThat(known.mode()).isEqualTo("EXISTING"); // Matching a quote alone does not establish a correct role.
    }

    @Test void rejectsInventedEvidenceOutOfCatalogAndMalformedLabels() throws Exception {
        for (String json : new String[] {
                "{\"mode\":\"NEW\",\"role\":\"상품 운영\",\"evidence\":\"invented\"}",
                "{\"mode\":\"EXISTING\",\"role\":\"임의 직무\",\"evidence\":\"Manager\"}",
                "{\"mode\":\"NEW\",\"role\":\"인사 운영\",\"evidence\":\"Manager\"}",
                "{\"mode\":\"NEW\",\"role\":\"<script>\",\"evidence\":\"Manager\"}",
                "{\"mode\":\"NEW\",\"role\":\"직무 확인 필요\",\"evidence\":\"Manager\"}",
                "{\"mode\":\"NEW\",\"role\":\"상품 운영\",\"evidence\":\"Manager\",\"extra\":true}"
        }) {
            var output = mapper.readTree(json);
            assertThatThrownBy(() -> JobRoleAiRules.validate(output, SOURCE)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void abstentionMustNotContainAProposedRole() throws Exception {
        assertThat(JobRoleAiRules.validate(mapper.readTree("""
                {"mode":"ABSTAIN","role":"","evidence":""}
                """), SOURCE).mode()).isEqualTo("ABSTAIN");
        var invalid = mapper.readTree("""
                {"mode":"ABSTAIN","role":"상품 운영","evidence":"Manager"}
                """);
        assertThatThrownBy(() -> JobRoleAiRules.validate(invalid, SOURCE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void previouslyApprovedNewRoleCanBeSelectedFromTheCatalog() throws Exception {
        var roles = new java.util.HashSet<>(JobRoleAiRules.EXISTING_ROLES);
        roles.add("상품 운영 매니저");
        var output = mapper.readTree("""
                {"mode":"EXISTING","role":"상품 운영 매니저","evidence":"Lead and coach operators"}
                """);
        assertThat(JobRoleAiRules.validate(output, SOURCE, roles).role()).isEqualTo("상품 운영 매니저");
        assertThatThrownBy(() -> JobRoleAiRules.validate(output, SOURCE)).hasMessage("INVALID_ROLE_CATALOG");
    }

    @Test void retailPricingRequiresPricingEvidenceInTheQuotedPassage() throws Exception {
        var unsupported = mapper.readTree("""
                {"mode":"EXISTING","role":"리테일 가격 운영","evidence":"Lead and coach operators on throughput, accuracy and turnaround."}
                """);
        assertThatThrownBy(() -> JobRoleAiRules.requirePricingEvidence(JobRoleAiRules.validate(unsupported, SOURCE))).hasMessage("INVALID_ROLE_EVIDENCE");
        assertThat(JobRoleAiRules.validate(unsupported, SOURCE).role()).isEqualTo("리테일 가격 운영");
        var supported = mapper.readTree("""
                {"mode":"EXISTING","role":"리테일 가격 운영","evidence":"Manage retail pricing and promotions."}
                """);
        assertThat(JobRoleAiRules.requirePricingEvidence(JobRoleAiRules.validate(supported, "Manage retail pricing and promotions.")).role()).isEqualTo("리테일 가격 운영");
    }
}
