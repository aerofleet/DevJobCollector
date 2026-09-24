package kr.itsdev.devjobcollector.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AdminTotpVerifierTest {
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void verifiesRfc6238VectorUsingSixDigits() {
        AdminTotpVerifier verifier = new AdminTotpVerifier(
                Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));

        assertThat(verifier.verify(RFC_SECRET, "287082")).isTrue();
        assertThat(verifier.verify(RFC_SECRET, "287083")).isFalse();
        assertThat(verifier.verify(RFC_SECRET, "not-six-digits")).isFalse();
    }

    @Test
    void rejectsInvalidBase32SecretWithoutLeakingDetails() {
        AdminTotpVerifier verifier = new AdminTotpVerifier(
                Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));

        assertThat(verifier.verify("INVALID!", "287082")).isFalse();
    }
}
