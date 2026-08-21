package kr.itsdev.devjobcollector.controller;

import static org.assertj.core.api.Assertions.assertThat;

import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {

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
