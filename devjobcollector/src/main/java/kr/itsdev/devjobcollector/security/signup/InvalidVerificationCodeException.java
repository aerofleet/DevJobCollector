package kr.itsdev.devjobcollector.security.signup;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InvalidVerificationCodeException extends ResponseStatusException {
    InvalidVerificationCodeException() {
        super(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다.");
    }
}
