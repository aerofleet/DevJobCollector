package kr.itsdev.devjobcollector.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class BusinessNumberProtectionTest {

    @Test
    void normalizesHyphensAndProducesOnlyHashAndMaskedValue() {
        var formatted = BusinessNumberProtection.protect("123-45-67890");
        var digitsOnly = BusinessNumberProtection.protect("1234567890");

        assertThat(formatted.hash()).hasSize(64).isEqualTo(digitsOnly.hash());
        assertThat(formatted.masked()).isEqualTo("***-**-67890");
        assertThat(formatted.toString()).doesNotContain("1234567890", "123-45-67890");
    }

    @Test
    void rejectsMalformedBusinessNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BusinessNumberProtection.protect("123-AB-67890"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BusinessNumberProtection.protect("123456789"));
    }
}
