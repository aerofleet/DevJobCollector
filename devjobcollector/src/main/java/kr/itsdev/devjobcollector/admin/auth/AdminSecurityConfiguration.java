package kr.itsdev.devjobcollector.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminSecurityProperties.class)
public class AdminSecurityConfiguration {
    @Bean
    AdminSessionAuthenticationFilter adminSessionAuthenticationFilter(
            AdminAuthenticationService authenticationService,
            AdminSecurityProperties properties) {
        return new AdminSessionAuthenticationFilter(authenticationService, properties);
    }

    @Bean
    AdminRequestSecurityFilter adminRequestSecurityFilter(AdminSecurityProperties properties,
                                                          AdminTokenCodec tokenCodec,
                                                          ObjectMapper objectMapper) {
        return new AdminRequestSecurityFilter(properties, tokenCodec, objectMapper);
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            AdminSessionAuthenticationFilter adminSessionAuthenticationFilter,
            AdminRequestSecurityFilter adminRequestSecurityFilter,
            AuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        return http
                .securityMatcher("/api/v1/admin/auth/**", "/api/v1/admin/me",
                        "/api/v1/admin/dashboard/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/admin/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(adminSessionAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminRequestSecurityFilter,
                        AdminSessionAuthenticationFilter.class)
                .build();
    }
}
