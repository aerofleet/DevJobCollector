package kr.itsdev.devjobcollector.dto.auth;

public record MemberMeResponse(
        Long id,
        String email,
        String name,
        String role,
        String profileStatus
) {
}
