package kr.itsdev.devjobcollector.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccountLinkRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountLinkRequired(
            AccountLinkRequiredException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                AccountLinkRequiredException.ERROR_CODE,
                "기존 계정으로 재인증한 뒤 계정을 연결해야 합니다.",
                request.getRequestURI()
        ));
    }
}
