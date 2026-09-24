package kr.itsdev.devjobcollector.admin.auth;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AdminTotpVerifier {
    private final Clock clock;

    public AdminTotpVerifier() {
        this(Clock.systemUTC());
    }

    AdminTotpVerifier(Clock clock) {
        this.clock = clock;
    }

    public boolean verify(String base32Secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        byte[] key;
        try {
            key = decodeBase32(base32Secret);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        long counter = clock.instant().getEpochSecond() / 30;
        for (long offset = -1; offset <= 1; offset++) {
            if (generate(key, counter + offset).equals(code)) return true;
        }
        return false;
    }

    private String generate(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP algorithm is unavailable", exception);
        }
    }

    public static byte[] decodeBase32(String value) {
        if (value == null) throw new IllegalArgumentException("secret is required");
        String normalized = value.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("secret is required");
        byte[] result = new byte[(normalized.length() * 5) / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char character : normalized.toCharArray()) {
            int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("invalid base32 secret");
            buffer = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
