package kr.itsdev.devjobcollector.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminRequestSecurityFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = "adminRequestId";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final AdminSecurityProperties properties;
    private final AdminTokenCodec tokenCodec;
    private final ObjectMapper objectMapper;

    public AdminRequestSecurityFilter(AdminSecurityProperties properties,
                                      AdminTokenCodec tokenCodec, ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenCodec = tokenCodec;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        if (!SAFE_METHODS.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin == null || !properties.getAllowedOrigins().contains(origin)) {
                reject(response, 403, "ADMIN_ORIGIN_DENIED", requestId);
                return;
            }
            if (!request.getRequestURI().endsWith("/auth/login")) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
                    String csrfToken = request.getHeader(properties.getCsrfHeaderName());
                    if (csrfToken == null || !tokenCodec.matchesHash(csrfToken, principal.csrfHash())) {
                        reject(response, 403, "ADMIN_CSRF_INVALID", requestId);
                        return;
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String requestId)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), java.util.Map.of(
                "code", code, "message", "관리자 보안 검증에 실패했습니다.",
                "requestId", requestId));
    }
}
