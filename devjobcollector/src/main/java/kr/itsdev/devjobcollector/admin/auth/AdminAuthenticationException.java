package kr.itsdev.devjobcollector.admin.auth;

import org.springframework.http.HttpStatus;

public class AdminAuthenticationException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public AdminAuthenticationException(HttpStatus status, String errorCode) {
        super(errorCode);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}
