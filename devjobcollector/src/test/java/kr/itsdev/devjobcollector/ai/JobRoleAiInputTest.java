package kr.itsdev.devjobcollector.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class JobRoleAiInputTest {
    private static final String MODEL = "gpt-5.4-mini";

    @Test void extractsKraftonDutiesAndRequiredQualifications() {
        String raw = "회사 소개\n" + "소개 내용 ".repeat(800)
                + "\n우리 팀과 함께할 미션을 소개합니다.\n• AI 투자 기회를 발굴합니다."
                + "\n이런 경험을 가진 분과 함께 성장하고 싶습니다! (필수요건)\n• 투자 분석 경력 7년 이상."
                + "\n이런 경험들이 있다면 저희가 찾는 그 분입니다! (우대요건)\n• MBA 우대.\n전형 안내";
        var input = JobRoleAiInput.prepareCompact(14428, "GREENHOUSE", "AI Investment & Strategy Analyst", "Management Supporting", raw, "v1", MODEL, JobRoleAiRules.EXISTING_ROLES);
        assertThat(input.content()).contains("AI 투자 기회를 발굴합니다.", "투자 분석 경력 7년 이상.")
                .doesNotContain("소개 내용", "MBA 우대", "전형 안내");
    }

    @Test void normalizesPublicBodyAndBindsHashToJobContentAndPolicy() {
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations", "Retail Selection",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL);
        assertThat(input.content()).contains("Run selection operations.", "Lead and coach operators.");
        assertThat(input.evidenceSource()).contains("Manager, Selection Operations", "Retail Selection", "Lead and coach operators.");
        assertThat(input.inputHash()).hasSize(64);
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations", "Retail Selection",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL).inputHash()).isEqualTo(input.inputHash());
        assertThat(JobRoleAiInput.prepare(13759, "GREENHOUSE", "Manager, Selection Operations", "Retail Selection",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL).inputHash()).isNotEqualTo(input.inputHash());
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations", "Retail Selection",
                "<p>Changed duties.</p>", "v1", MODEL).inputHash()).isNotEqualTo(input.inputHash());
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations", "Retail Selection",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v2", MODEL).inputHash()).isNotEqualTo(input.inputHash());
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations", "Different category",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL).inputHash()).isNotEqualTo(input.inputHash());
        var expanded = new java.util.HashSet<>(JobRoleAiRules.EXISTING_ROLES);
        expanded.add("상품 운영 매니저");
        assertThat(JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager, Selection Operations", "Retail Selection",
                "<p>Run selection operations.</p><p>Lead and coach operators.</p>", "v1", MODEL, expanded).inputHash()).isNotEqualTo(input.inputHash());
    }

    @Test void declinesMissingOrOverlongContentRatherThanGuessing() {
        assertThatThrownBy(() -> JobRoleAiInput.prepare(1, "GREENHOUSE", "Manager", null, " ", "v1", MODEL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobRoleAiInput.prepare(1, "GREENHOUSE", "Manager", null, "x".repeat(4001), "v1", MODEL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobRoleAiInput.prepare(1, "GREENHOUSE", "Manager", null, "duties", "v1", "gpt-5-nano"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void longJobUsesOnlyClearlyDelimitedDutiesAndRequiredQualifications() {
        String raw = "About us\n" + "Company marketing. ".repeat(250)
                + "\nJob Description\nRun selection operations.\nBasic Qualifications\nFive years in retail operations."
                + "\nPreferred Qualifications\nOptional SQL experience.\nBenefits\nBenefits information.";
        var input = JobRoleAiInput.prepare(13758, "GREENHOUSE", "Selection Operations Manager", "Retail Selection", raw, "v1", MODEL);
        assertThat(input.content()).contains("Run selection operations.", "Five years in retail operations.")
                .doesNotContain("Company marketing", "Optional SQL", "Benefits information");
        assertThatThrownBy(() -> JobRoleAiInput.prepare(13758, "GREENHOUSE", "Manager", null,
                "Company marketing. ".repeat(250) + "\nJob Description\nRun operations.", "v1", MODEL))
                .hasMessage("ROLE_CONTENT_UNUSABLE");
    }

    @Test void compactInputKeepsOriginalHashReadableAndRequiresBothSections() {
        String raw = "회사 소개\n소개 문구\n업무 내용\n상품 규제를 검토합니다.\n자격 요건\n운영 경력 3년 이상.\n우대 사항\n외국어 능력.";
        var roles = JobRoleAiRules.EXISTING_ROLES;
        var original = JobRoleAiInput.prepare(1, "GREENHOUSE", "운영 담당", "", raw, "v1", MODEL, roles);
        var compact = JobRoleAiInput.prepareCompact(1, "GREENHOUSE", "운영 담당", "", raw, "v1", MODEL, roles);
        assertThat(compact.content()).contains("상품 규제를 검토합니다.", "운영 경력 3년 이상.").doesNotContain("소개 문구", "외국어 능력");
        assertThat(compact.inputHash()).isNotEqualTo(original.inputHash());
        assertThat(JobRoleAiInput.prepareMatching(1, "GREENHOUSE", "운영 담당", "", raw, "v1", MODEL, roles, original.inputHash())).isEqualTo(original);
        assertThat(JobRoleAiInput.prepareMatching(1, "GREENHOUSE", "운영 담당", "", raw, "v1", MODEL, roles, compact.inputHash())).isEqualTo(compact);
        assertThatThrownBy(() -> JobRoleAiInput.prepareCompact(1, "GREENHOUSE", "운영 담당", "", "업무 내용\n상품 검토", "v1", MODEL, roles))
                .hasMessage("ROLE_CONTENT_UNUSABLE");
    }

    @Test void longElasticStyleJobExtractsDutiesAndQualificationsWithoutBenefits() {
        String raw = "About us\n" + "Company information. ".repeat(210)
                + "\nWhat You Will Be Doing\nManage partner sales and grow joint revenue."
                + "\nWhat You Bring\nEight years of partner management experience."
                + "\nAdditional Information - We Take Care of Our People\nBenefits and legal notices.";
        var input = JobRoleAiInput.prepare(13969, "GREENHOUSE", "Senior Partner Sales Manager", "Partners - APJ", raw, "v1", MODEL);
        assertThat(input.content()).contains("Manage partner sales", "Eight years of partner management")
                .doesNotContain("Company information", "Benefits and legal notices");
    }

    @Test void longCloudflareStyleJobExtractsDutiesAndQualificationsWithoutCompanyPromotion() {
        String raw = "About us\n" + "Company information. ".repeat(210)
                + "\nKey Responsibilities :\nSupport payroll and onboarding."
                + "\nSkill Requirements & Qualifications :\nCurrently pursuing an MBA."
                + "\nTechnical & Functional Skills :\nExperience with spreadsheets."
                + "\nWhat Makes Cloudflare Special?\n" + "Company information. ".repeat(210);
        var input = JobRoleAiInput.prepare(13972, "GREENHOUSE", "People Operations Intern", "People & Places", raw, "v1", MODEL);
        assertThat(input.content()).contains("Support payroll and onboarding", "Currently pursuing an MBA", "Experience with spreadsheets")
                .doesNotContain("Company information");
    }
}
