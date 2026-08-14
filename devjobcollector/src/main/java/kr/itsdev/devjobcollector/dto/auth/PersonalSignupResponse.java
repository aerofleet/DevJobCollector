package kr.itsdev.devjobcollector.dto.auth;

public record PersonalSignupResponse(
        String email,
        String status,
        int verificationExpiresMinutes,
        String developmentVerificationCode
) {
}
