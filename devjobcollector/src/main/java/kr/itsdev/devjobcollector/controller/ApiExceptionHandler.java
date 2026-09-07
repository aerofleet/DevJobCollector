package kr.itsdev.devjobcollector.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import kr.itsdev.devjobcollector.company.CompanyAlreadyExistsException;
import kr.itsdev.devjobcollector.company.CompanyVerificationException;
import kr.itsdev.devjobcollector.security.service.MemberAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CompanyVerificationException.class)
    public ResponseEntity<ApiErrorResponse> handleCompanyVerification(
            CompanyVerificationException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                exception.getErrorCode(),
                "기업 인증 요청을 처리할 수 없습니다.",
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(CompanyAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleCompanyAlreadyExists(
            CompanyAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                CompanyAlreadyExistsException.ERROR_CODE,
                "이미 등록된 사업자번호입니다.",
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MemberAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleMemberAuthentication(
            MemberAuthenticationException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                exception.getErrorCode(),
                "회원 인증 상태를 확인할 수 없습니다.",
                request.getRequestURI()
        ));
    }

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
