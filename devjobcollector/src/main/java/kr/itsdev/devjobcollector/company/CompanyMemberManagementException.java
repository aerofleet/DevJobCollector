package kr.itsdev.devjobcollector.company;

import org.springframework.http.HttpStatus;

public class CompanyMemberManagementException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    private CompanyMemberManagementException(String errorCode, HttpStatus status) {
        super(errorCode);
        this.errorCode = errorCode;
        this.status = status;
    }

    public static CompanyMemberManagementException memberNotFound() {
        return new CompanyMemberManagementException("COMPANY_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public static CompanyMemberManagementException companyNotFound() {
        return new CompanyMemberManagementException("COMPANY_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public static CompanyMemberManagementException inviteeNotFound() {
        return new CompanyMemberManagementException("COMPANY_INVITEE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public static CompanyMemberManagementException memberAlreadyExists() {
        return new CompanyMemberManagementException("COMPANY_MEMBER_ALREADY_EXISTS", HttpStatus.CONFLICT);
    }

    public static CompanyMemberManagementException memberInactive() {
        return new CompanyMemberManagementException("COMPANY_MEMBER_INACTIVE", HttpStatus.CONFLICT);
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}
