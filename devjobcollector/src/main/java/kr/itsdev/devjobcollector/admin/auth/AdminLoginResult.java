package kr.itsdev.devjobcollector.admin.auth;

public record AdminLoginResult(
        Status status,
        String sessionToken,
        String csrfToken,
        AdminPrincipal principal
) {
    public enum Status { SUCCESS, INVALID_CREDENTIALS, LOCKED, DISABLED, MFA_NOT_CONFIGURED }

    public static AdminLoginResult success(String sessionToken, String csrfToken,
                                           AdminPrincipal principal) {
        return new AdminLoginResult(Status.SUCCESS, sessionToken, csrfToken, principal);
    }

    public static AdminLoginResult failure(Status status) {
        return new AdminLoginResult(status, null, null, null);
    }
}
