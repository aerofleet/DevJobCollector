package kr.itsdev.devjobcollector.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import kr.itsdev.devjobcollector.security.signup.AuthSignupProperties;
import kr.itsdev.auth.common.oauth.OAuth2CallbackExceptionFilter;

@Configuration
@EnableConfigurationProperties({AuthTokenProperties.class, AuthLocalLoginProperties.class, AuthSignupProperties.class})
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<OAuth2UserService<OAuth2UserRequest, OAuth2User>> commonOAuth2UserServiceProvider,
            ObjectProvider<AuthenticationSuccessHandler> socialLoginSuccessHandlerProvider,
            ObjectProvider<AuthenticationFailureHandler> socialLoginFailureHandlerProvider,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/signup/personal/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/resume/**", "/api/v1/members/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            OAuth2UserService<OAuth2UserRequest, OAuth2User> commonOAuth2UserService =
                    commonOAuth2UserServiceProvider.getIfAvailable();
            AuthenticationSuccessHandler socialLoginSuccessHandler =
                    socialLoginSuccessHandlerProvider.getIfAvailable();
            AuthenticationFailureHandler socialLoginFailureHandler =
                    socialLoginFailureHandlerProvider.getIfAvailable();

            if (commonOAuth2UserService != null
                    && socialLoginSuccessHandler != null
                    && socialLoginFailureHandler != null) {
                http.oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(commonOAuth2UserService))
                        .successHandler(socialLoginSuccessHandler)
                        .failureHandler(socialLoginFailureHandler)
                );
                http.addFilterBefore(
                        new OAuth2CallbackExceptionFilter(socialLoginFailureHandler),
                        OAuth2LoginAuthenticationFilter.class
                );
            }
        }

        return http.build();
    }
}
