package kr.itsdev.devjobcollector.company;

public class LastActiveOwnerException extends IllegalStateException {
    public LastActiveOwnerException() {
        super("LAST_ACTIVE_COMPANY_OWNER");
    }
}
