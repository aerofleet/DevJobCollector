package kr.itsdev.devjobcollector.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResumePiiGateTest {
    private final ResumePiiGate gate = new ResumePiiGate();

    @Test
    void masksKnownIdentityAndCommonIdentifiersWithoutMutatingEvidence() {
        var result = gate.mask("""
                이름: 홍길동
                연락처 010-1234-5678 / +82 10 2222 3333
                dev@example.com / 900101-1234567
                주소: 서울특별시 강남구 테스트로 123 101호
                https://github.com/private-user
                Java Spring MySQL: 응답시간 1200ms → 300ms
                홍길동의 프로젝트
                """, List.of("홍길동"));
        assertThat(result.maskedText()).doesNotContain("홍길동", "010-1234", "2222", "dev@example",
                "900101", "강남구", "private-user");
        assertThat(result.maskedText()).contains("Java Spring MySQL", "1200ms → 300ms");
        assertThat(result.piiCategorySummary()).containsKeys("PHONE", "EMAIL", "RESIDENT_ID", "URL", "IDENTITY_LINE");
        assertThat(result.maskedContentHash()).hasSize(64);
        assertThat(result.toString()).doesNotContain("MySQL", "1200");
    }

    @Test
    void normalizesFullWidthAndInvisibleCharactersBeforeMasking() {
        var result = gate.mask("０１０－１２３４－５６７８ a\u200Bb@example.com", List.of());
        assertThat(result.maskedText()).doesNotContain("1234", "@");
    }

    @Test
    void neverTreatsRegexRedactionAsPermissionToTransmit() {
        for (String input : List.of("Java Spring", "Jane Doe lives near an unnamed road", "홍길동 경력 3년")) {
            var result = gate.mask(input, List.of());
            assertThat(result.piiGatePassed()).isFalse();
            assertThat(result.blockReason()).isEqualTo("PII_COVERAGE_UNVERIFIED");
            assertThatThrownBy(() -> gate.requireExternalTransmission(result)).hasMessageContaining("BLOCKED_PII");
        }
        assertThatThrownBy(() -> gate.requireExternalTransmission(
                new ResumePiiGate.Result("raw", true, null, "forged", java.util.Map.of(), "hash")))
                .hasMessageContaining("BLOCKED_PII");
    }

    @Test
    void rejectsEmptyAndOversizeTextAndProducesStableHashes() {
        for (String input : List.of("", " ", "x".repeat(60_001))) {
            assertThatThrownBy(() -> gate.mask(input, List.of())).hasMessageContaining("INVALID_RESUME_TEXT");
        }
        assertThat(gate.mask("Java", List.of()).maskedContentHash())
                .isEqualTo(gate.mask("Java", List.of()).maskedContentHash());
    }
}
