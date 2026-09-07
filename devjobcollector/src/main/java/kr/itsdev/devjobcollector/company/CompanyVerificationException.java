package kr.itsdev.devjobcollector.company;

import org.springframework.http.HttpStatus;

public class CompanyVerificationException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    private CompanyVerificationException(String errorCode, HttpStatus status) {
        super(errorCode);
        this.errorCode = errorCode;
        this.status = status;
    }

    public static CompanyVerificationException companyNotFound() {
        return new CompanyVerificationException("COMPANY_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public static CompanyVerificationException requestNotFound() {
        return new CompanyVerificationException("COMPANY_VERIFICATION_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public static CompanyVerificationException ownerRequired() {
        return new CompanyVerificationException("COMPANY_OWNER_REQUIRED", HttpStatus.FORBIDDEN);
    }

    public static CompanyVerificationException platformAdminRequired() {
        return new CompanyVerificationException("PLATFORM_ADMIN_REQUIRED", HttpStatus.FORBIDDEN);
    }

    public static CompanyVerificationException pendingRequestExists() {
        return new CompanyVerificationException("COMPANY_VERIFICATION_PENDING_EXISTS", HttpStatus.CONFLICT);
    }

    public static CompanyVerificationException invalidCompanyStatus() {
        return new CompanyVerificationException("COMPANY_VERIFICATION_COMPANY_STATUS_INVALID", HttpStatus.CONFLICT);
    }

    public static CompanyVerificationException alreadyReviewed() {
        return new CompanyVerificationException("COMPANY_VERIFICATION_ALREADY_REVIEWED", HttpStatus.CONFLICT);
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}
