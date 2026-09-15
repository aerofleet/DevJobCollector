# auth-common

Google/GitHub OAuth2 로그인을 제공하고 Kakao/Naver/Apple 확장을 위한 Provider adapter
registry를 제공하는 공통 모듈입니다.

## Provider framework

- 활성 adapter: Google, GitHub, Kakao, Naver
- 예약 Provider ID: Google, GitHub, Kakao, Naver, Apple
- 신규 Provider는 `OAuth2ProfileAdapter` Bean을 추가하면 registry가 자동 등록합니다.
- 중복 Provider adapter는 애플리케이션 시작 시 거부합니다.
- 아직 adapter가 없는 Apple은 로그인 경로에서 활성화되지 않습니다.

Kakao는 OIDC adapter만 기본 등록되며 실제 ClientRegistration은 별도
`kakao` Spring profile로 격리되어 있습니다. 운영 자격증명과 redirect URI 등록 후
`prod,kakao`처럼 profile을 활성화합니다. 저장소에는 client ID/secret 기본값을
두지 않습니다.

Naver는 일반 OAuth2 adapter이며 공식 user-info 응답의 `response.id`를 불변
식별자로 사용합니다. 실제 ClientRegistration은 `naver` Spring profile로 격리되어
있고, 운영 자격증명과 callback URL 등록 후 `prod,naver`처럼 활성화합니다.

OAuth `state`는 서버 세션 registry에 저장합니다. 기본 TTL은 5분, 세션당 최대
대기 요청은 8개이며, 서로 다른 탭의 로그인 요청을 state별로 보존하고 callback에서
일회성으로 제거합니다.

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
    oauth-state-ttl: 5m
    oauth-state-max-pending: 8
```

실패 콜백은 `error` query parameter를 사용합니다. 계정 이메일 충돌은
`ACCOUNT_LINK_REQUIRED`, 그 밖의 OAuth 실패는 상세 정보를 숨긴
`OAUTH_LOGIN_FAILED`로 전달합니다.

## 5) 프론트 로그인 진입 URL

- `/oauth2/authorization/google`
- `/oauth2/authorization/github`
