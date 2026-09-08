package kr.itsdev.devjobcollector.company;

import org.springframework.http.HttpStatus;

public class CompanyAuthorizationException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    private CompanyAuthorizationException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
        this.status = HttpStatus.FORBIDDEN;
    }

    public static CompanyAuthorizationException accessDenied() {
        return new CompanyAuthorizationException("COMPANY_ACCESS_DENIED");
    }

    public static CompanyAuthorizationException companyNotVerified() {
        return new CompanyAuthorizationException("COMPANY_NOT_VERIFIED");
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}
