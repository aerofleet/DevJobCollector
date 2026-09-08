package kr.itsdev.devjobcollector.controller;

import static org.assertj.core.api.Assertions.assertThat;

import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import kr.itsdev.devjobcollector.company.CompanyAlreadyExistsException;
import kr.itsdev.devjobcollector.company.CompanyAuthorizationException;
import kr.itsdev.devjobcollector.company.CompanyMemberManagementException;
import kr.itsdev.devjobcollector.company.LastActiveOwnerException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {

    @Test
    void mapsCompanyMemberConflictToStableBody() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/companies/7/members/invitations");

        var response = new ApiExceptionHandler().handleCompanyMemberManagement(
                CompanyMemberManagementException.memberAlreadyExists(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                409, "COMPANY_MEMBER_ALREADY_EXISTS",
                "기업 멤버 요청을 처리할 수 없습니다.",
                "/api/v1/companies/7/members/invitations"));
    }

    @Test
    void mapsLastActiveOwnerToStableConflictBody() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/v1/companies/7/members/11");

        var response = new ApiExceptionHandler().handleLastActiveOwner(
                new LastActiveOwnerException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                409, "LAST_ACTIVE_COMPANY_OWNER",
                "마지막 활성 OWNER는 변경하거나 제거할 수 없습니다.",
                "/api/v1/companies/7/members/11"));
    }

    @Test
    void mapsCompanyAuthorizationFailureToStableForbiddenBody() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH", "/api/v1/companies/7");

        var response = new ApiExceptionHandler().handleCompanyAuthorization(
                CompanyAuthorizationException.companyNotVerified(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                403,
                "COMPANY_NOT_VERIFIED",
                "기업 리소스에 접근할 수 없습니다.",
                "/api/v1/companies/7"
        ));
    }

    @Test
    void mapsCompanyDuplicateToStableConflictBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/companies");

        var response = new ApiExceptionHandler().handleCompanyAlreadyExists(
                new CompanyAlreadyExistsException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                409,
                "COMPANY_ALREADY_EXISTS",
                "이미 등록된 사업자번호입니다.",
                "/api/v1/companies"
        ));
    }

    @Test
    void mapsAccountLinkRequiredToStableConflictBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/social");

        var response = new ApiExceptionHandler().handleAccountLinkRequired(
                new AccountLinkRequiredException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                409,
                "ACCOUNT_LINK_REQUIRED",
                "기존 계정으로 재인증한 뒤 계정을 연결해야 합니다.",
                "/api/v1/auth/social"
        ));
    }
}
