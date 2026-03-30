package kr.itsdev.auth.common.model;

public record AuthenticatedUser(
        Long id,
        String email,
        String name,
        String role
) {
}
