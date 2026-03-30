package kr.itsdev.auth.common.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import kr.itsdev.auth.common.config.AuthCommonProperties;
import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.spi.TokenIssueService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

public class SocialLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthCommonProperties properties;
    private final TokenIssueService tokenIssueService;

    public SocialLoginSuccessHandler(AuthCommonProperties properties, TokenIssueService tokenIssueService) {
        this.properties = properties;
        this.tokenIssueService = tokenIssueService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        AuthenticatedUser user = extractUser(principal);
        String accessToken = tokenIssueService.issueAccessToken(user);

        String redirectUri = buildRedirectUri(accessToken);
        response.sendRedirect(redirectUri);
    }

    private AuthenticatedUser extractUser(OAuth2User principal) {
        Object idRaw = principal.getAttributes().get(AuthCommonAttributeKeys.APP_USER_ID);
        Long id = (idRaw instanceof Number number) ? number.longValue() : parseLong(idRaw);

        String email = stringValue(principal.getAttributes().get(AuthCommonAttributeKeys.APP_USER_EMAIL));
        String name = stringValue(principal.getAttributes().get(AuthCommonAttributeKeys.APP_USER_NAME));
        String role = stringValue(principal.getAttributes().get(AuthCommonAttributeKeys.APP_USER_ROLE));

        return new AuthenticatedUser(id, email, name, role);
    }

    private String buildRedirectUri(String accessToken) {
        String encoded = URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        String base = properties.getFrontendSuccessUri();
        String param = properties.getTokenQueryParam();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + param + "=" + encoded;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
