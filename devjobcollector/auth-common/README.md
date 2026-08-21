# auth-common

Google/GitHub OAuth2 로그인 공통 모듈입니다.

## 1) 모듈 추가

- 멀티모듈 `settings.gradle`
  - `include 'auth-common'`
- 서비스 모듈 `build.gradle`
  - `implementation project(':auth-common')`

## 2) 애플리케이션에서 구현해야 하는 SPI

```java
@Service
public class SocialUserUpsertServiceImpl implements SocialUserUpsertService {
    @Override
    public AuthenticatedUser upsert(SocialProfile profile) {
        // provider/providerUserId 기준 조회 후 생성/갱신
        // role 예: USER
        return new AuthenticatedUser(1L, profile.email(), profile.name(), "USER");
    }
}
```

```java
@Service
public class TokenIssueServiceImpl implements TokenIssueService {
    @Override
    public String issueAccessToken(AuthenticatedUser user) {
        // JWT 발급
        return "issued-jwt-token";
    }
}
```

## 3) Security 설정에서 oauth2Login 연결

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> commonOAuth2UserService,
            AuthenticationSuccessHandler socialLoginSuccessHandler,
            AuthenticationFailureHandler socialLoginFailureHandler
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(commonOAuth2UserService))
                .successHandler(socialLoginSuccessHandler)
                .failureHandler(socialLoginFailureHandler)
            );
        return http.build();
    }
}
```

## 4) application.yml

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: profile, email
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: read:user, user:email

auth:
  common:
    frontend-success-uri: https://<FRONTEND_DOMAIN>/oauth/callback
    frontend-failure-uri: https://<FRONTEND_DOMAIN>/oauth/callback
    token-query-param: token
```

실패 콜백은 `error` query parameter를 사용합니다. 계정 이메일 충돌은
`ACCOUNT_LINK_REQUIRED`, 그 밖의 OAuth 실패는 상세 정보를 숨긴
`OAUTH_LOGIN_FAILED`로 전달합니다.

## 5) 프론트 로그인 진입 URL

- `/oauth2/authorization/google`
- `/oauth2/authorization/github`
