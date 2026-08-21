package kr.itsdev.auth.common.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import kr.itsdev.auth.common.config.AuthCommonProperties;
import kr.itsdev.auth.common.exception.AccountLinkRequiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class SocialLoginFailureHandler implements AuthenticationFailureHandler {
    private static final String FALLBACK_ERROR_CODE = "OAUTH_LOGIN_FAILED";

    private final AuthCommonProperties properties;

    public SocialLoginFailureHandler(AuthCommonProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        response.sendRedirect(buildRedirectUri(errorCode(exception)));
    }

    private String errorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthError
                && oauthError.getError() != null
                && AccountLinkRequiredException.ERROR_CODE.equals(oauthError.getError().getErrorCode())) {
            return AccountLinkRequiredException.ERROR_CODE;
        }
        return FALLBACK_ERROR_CODE;
    }

    private String buildRedirectUri(String errorCode) {
        String encoded = URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        String base = properties.getFrontendFailureUri();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "error=" + encoded;
    }
}
