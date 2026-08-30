package kr.itsdev.devjobcollector.security.service;

public class MemberAuthenticationException extends RuntimeException {
    private final String errorCode;

    public MemberAuthenticationException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
