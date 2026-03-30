package kr.itsdev.devjobcollector.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
}
