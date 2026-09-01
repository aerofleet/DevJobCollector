package kr.itsdev.auth.common.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuth2CallbackExceptionFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(OAuth2CallbackExceptionFilter.class);
    private static final String CALLBACK_PREFIX = "/login/oauth2/code/";
    private static final String FALLBACK_ERROR_CODE = "OAUTH_LOGIN_FAILED";

    private final AuthenticationFailureHandler failureHandler;

    public OAuth2CallbackExceptionFilter(AuthenticationFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(CALLBACK_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException | ServletException exception) {
            log.error(
                    "OAuth2 callback processing failed outside authentication handlers: "
                            + "exceptionType={}, rootCauseType={}",
                    exception.getClass().getName(),
                    rootCauseType(exception)
            );
            handleFailure(request, response, exception);
        }
    }

    private void handleFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception exception
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        try {
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(
                            new OAuth2Error(FALLBACK_ERROR_CODE),
                            exception
                    )
            );
        } catch (RuntimeException | ServletException | IOException handlerException) {
            log.error(
                    "OAuth2 callback failure response failed: exceptionType={}, rootCauseType={}",
                    handlerException.getClass().getName(),
                    rootCauseType(handlerException)
            );
            writeSafeFallback(response);
        }
    }

    private void writeSafeFallback(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"OAUTH_LOGIN_FAILED\"}");
        response.flushBuffer();
    }

    private String rootCauseType(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }
}
