package kr.itsdev.devjobcollector.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AdminMfaSecretCipherTest {
    @Test
    void encryptsWithRandomNonceAndDecryptsWithoutPersistingPlaintext() {
        AdminSecurityProperties properties = new AdminSecurityProperties();
        properties.setMfaEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        AdminMfaSecretCipher cipher = new AdminMfaSecretCipher(properties);

        byte[] first = cipher.encrypt("JBSWY3DPEHPK3PXP");
        byte[] second = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(first).isNotEqualTo(second);
        assertThat(new String(first, java.nio.charset.StandardCharsets.US_ASCII))
                .doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void requiresDedicated256BitEncryptionKey() {
        AdminSecurityProperties properties = new AdminSecurityProperties();
        properties.setMfaEncryptionKey(Base64.getEncoder().encodeToString(new byte[16]));
        AdminMfaSecretCipher cipher = new AdminMfaSecretCipher(properties);

        assertThatThrownBy(() -> cipher.encrypt("JBSWY3DPEHPK3PXP"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32-byte key");
    }
}
