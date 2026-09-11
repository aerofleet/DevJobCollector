package kr.itsdev.devjobcollector.security.hardening;

public class SecurityRateLimitException extends RuntimeException {
    public static final String ERROR_CODE = "SECURITY_ACTION_RATE_LIMITED";

    public SecurityRateLimitException() {
        super(ERROR_CODE);
    }
}
