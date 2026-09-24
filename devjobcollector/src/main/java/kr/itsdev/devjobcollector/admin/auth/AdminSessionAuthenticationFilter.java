package kr.itsdev.devjobcollector.admin.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminSessionAuthenticationFilter extends OncePerRequestFilter {
    private final AdminAuthenticationService authenticationService;
    private final AdminSecurityProperties properties;

    public AdminSessionAuthenticationFilter(AdminAuthenticationService authenticationService,
                                            AdminSecurityProperties properties) {
        this.authenticationService = authenticationService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = cookieValue(request, properties.getSessionCookieName());
        AdminPrincipal principal = authenticationService.authenticate(token);
        if (principal != null) {
            var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
            principal.permissions().forEach(permission -> authorities.add(
                    new SimpleGrantedAuthority("ADMIN_" + permission.name())));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, List.copyOf(authorities));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    public static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
