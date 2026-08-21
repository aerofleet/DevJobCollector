package kr.itsdev.auth.common.exception;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public class AccountLinkRequiredException extends OAuth2AuthenticationException {
    public static final String ERROR_CODE = "ACCOUNT_LINK_REQUIRED";

    public AccountLinkRequiredException() {
        super(new OAuth2Error(ERROR_CODE), ERROR_CODE);
    }
}
