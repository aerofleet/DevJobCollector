package kr.itsdev.devjobcollector.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonalSignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 2, max = 50) String name,
        @NotBlank @Size(min = 8, max = 72) String password,
        @AssertTrue(message = "필수 약관에 동의해야 합니다.") boolean termsAccepted,
        @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다.") boolean privacyAccepted,
        String turnstileToken
) {
}
