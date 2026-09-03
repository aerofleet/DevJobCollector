package kr.itsdev.devjobcollector.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthCareerObservationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthCareerObservationFilter.class);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        ObservationScope scope = scope(request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (scope != null) {
                log.info(
                        "AUTH_CAREER_OBSERVATION scope={} method={} path={} status={}",
                        scope,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus()
                );
            }
        }
    }

    static ObservationScope scope(String path) {
        if (path == null) {
            return null;
        }
        if (startsWithAny(
                path,
                "/api/v1/members/me/bookmarks",
                "/api/v1/members/me/recent-jobs",
                "/api/v1/members/me/applications",
                "/api/v1/members/me/resumes"
        )) {
            return ObservationScope.CAREER;
        }
        if (path.equals("/api/v1/members/me")
                || startsWithAny(path, "/api/v1/auth", "/oauth2", "/login/oauth2")) {
            return ObservationScope.AUTH;
        }
        return null;
    }

    private static boolean startsWithAny(String path, String... prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    enum ObservationScope {
        AUTH,
        CAREER
    }
}
