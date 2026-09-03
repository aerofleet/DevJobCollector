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
    private static final String ACCOUNT_LINK_INVALID = "ACCOUNT_LINK_INVALID";
    private static final String ACCOUNT_LINK_CONFLICT = "ACCOUNT_LINK_CONFLICT";

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
        response.sendRedirect(buildRedirectUri(errorCode(exception), provider(request)));
    }

    private String errorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthError && oauthError.getError() != null) {
            String code = oauthError.getError().getErrorCode();
            if (AccountLinkRequiredException.ERROR_CODE.equals(code)
                    || ACCOUNT_LINK_INVALID.equals(code)
                    || ACCOUNT_LINK_CONFLICT.equals(code)) {
                return code;
            }
        }
        return FALLBACK_ERROR_CODE;
    }

    private String buildRedirectUri(String errorCode, String provider) {
        String encoded = URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        String base = properties.getFrontendFailureUri();
        String separator = base.contains("?") ? "&" : "?";
        String redirect = base + separator + "error=" + encoded;
        if (provider == null) {
            return redirect;
        }
        return redirect + "&provider=" + URLEncoder.encode(provider, StandardCharsets.UTF_8);
    }

    private String provider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/login/oauth2/code/";
        int index = uri.indexOf(prefix);
        if (index < 0) {
            return null;
        }
        String value = uri.substring(index + prefix.length());
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        return value.matches("[a-zA-Z0-9_-]+") ? value.toLowerCase() : null;
    }
}
