package kr.itsdev.devjobcollector.company;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class BusinessNumberProtection {
    private BusinessNumberProtection() {
    }

    static ProtectedBusinessNumber protect(String value) {
        if (value == null || !value.matches("\\d{3}-?\\d{2}-?\\d{5}")) {
            throw new IllegalArgumentException("businessNumber must contain exactly 10 digits");
        }
        String digits = value.replace("-", "");
        return new ProtectedBusinessNumber(sha256(digits), "***-**-" + digits.substring(5));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    record ProtectedBusinessNumber(String hash, String masked) {
    }
}
