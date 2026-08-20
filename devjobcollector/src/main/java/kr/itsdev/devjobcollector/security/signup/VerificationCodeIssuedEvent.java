package kr.itsdev.devjobcollector.security.signup;

public record VerificationCodeIssuedEvent(String email, String code) {
}
