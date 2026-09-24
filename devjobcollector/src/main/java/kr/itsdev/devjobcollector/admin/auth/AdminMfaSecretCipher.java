package kr.itsdev.devjobcollector.admin.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AdminMfaSecretCipher {
    private static final int NONCE_LENGTH = 12;
    private final AdminSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminMfaSecretCipher(AdminSecurityProperties properties) {
        this.properties = properties;
    }

    public byte[] encrypt(String secret) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("failed to encrypt admin MFA secret", exception);
        }
    }

    public String decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= NONCE_LENGTH) {
            throw new IllegalStateException("admin MFA is not configured");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            byte[] nonce = new byte[NONCE_LENGTH];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce).doFinal(encrypted),
                    StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("failed to decrypt admin MFA secret", exception);
        }
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(properties.getMfaEncryptionKey());
        if (key.length != 32) {
            throw new IllegalStateException("ADMIN_MFA_ENCRYPTION_KEY must be a base64 encoded 32-byte key");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }
}
