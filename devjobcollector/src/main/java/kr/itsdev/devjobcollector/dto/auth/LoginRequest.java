package kr.itsdev.devjobcollector.dto.auth;

public record LoginRequest(
        String identifier,
        String password
) {
}
