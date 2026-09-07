package kr.itsdev.devjobcollector.company;

public class CompanyAlreadyExistsException extends RuntimeException {
    public static final String ERROR_CODE = "COMPANY_ALREADY_EXISTS";

    public CompanyAlreadyExistsException() {
        super(ERROR_CODE);
    }

    public CompanyAlreadyExistsException(Throwable cause) {
        super(ERROR_CODE, cause);
    }
}
