# Multi-Provider P7-02 Kakao OIDC 검증

## 1. 범위

- 작업: P7-02 Kakao OIDC adapter와 통합 테스트
- 구현일: 2026-09-14, 운영 검증 완료: 2026-09-15
- 포함:
  - Kakao `sub`, `iss`, profile, `email_verified` claim 변환
  - Spring Security OIDC 검증 완료 후 DJC identity upsert
  - RS256 signature, issuer, audience, expiration, state/nonce 계약 평가
  - 자격증명 기본값이 없는 `kakao` 전용 profile
  - 신규 Kakao identity의 이메일 누락·미검증 fail-closed
- 제외:
  - 실제 Kakao 앱 자격증명과 redirect URI 등록
  - 운영 `kakao` profile 활성화
  - Kakao account link/unlink와 로그인 UI

## 2. 공식 계약

- Kakao OIDC issuer: `https://kauth.kakao.com`
- 식별자: ID Token/UserInfo의 `sub`
- ID Token 서명: RS256, 공개키는 discovery의 `jwks_uri`
- 검증: signature, issuer, audience, expiration, nonce
- UserInfo 이메일 증거: `email_verified`

참조:

- https://developers.kakao.com/docs/ko/kakaologin/rest-api
- https://developers.kakao.com/docs/ko/kakaologin/utilize
- https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html

2026-09-14 discovery 실측에서 issuer, authorization/token/userinfo/JWKS endpoint와
RS256 지원이 코드 설정과 일치했다.

## 3. KPI와 OKR

| 항목 | 목표 KPI | 결과 | 판정 |
|---|---:|---:|---|
| Kakao 신규 평가셋 | 16/16 | 16/16 | 통과 |
| auth-common OAuth 회귀 | 오류율 0% | 20/20, 오류 0 | 통과 |
| ID Token 위조 차단 | 100% | 잘못된 signature/issuer/audience/expiration 4/4 차단 | 통과 |
| nonce/state 생성 | 요청별 고유값 100% | 2/2 고유 state, nonce 존재 | 통과 |
| 미검증 신규 계정 차단 | 3/3 | 3/3, DB write 0 | 통과 |
| 비활성 Provider 시작 경로 | 5xx 0건, HTTP 404 | 500→404, 최종 1/1 | 통과 |
| 전체 Gradle | 성공 | 494건 중 407 passed·87 환경 조건 skip | 통과 |
| secret/token 원문 저장·로그 | 0건 | 0건 | 통과 |

- OKR 연결: 2026-10-30 Multi-Provider 목표 중 세 번째 활성 adapter를 완성하고
  신규 Provider의 account takeover 및 ID Token replay 위험을 공통 OIDC 경로에서 차단한다.

## 4. 평가셋

1. 정상 Kakao claim을 `provider=KAKAO + sub`로 변환한다.
2. 선택 claim 미제공을 허용하되 신규 가입은 검증 이메일이 없으면 차단한다.
3. issuer 불일치를 adapter와 OIDC validator에서 차단한다.
4. audience와 expiration 불일치를 차단한다.
5. 로컬 RSA 정상 서명은 통과하고 위조 RS256 서명은 차단한다.
6. authorization request마다 state와 nonce를 생성한다.
7. 검증된 OIDC principal만 DJC upsert와 JWT 성공 handler용 principal로 변환한다.
8. 신규 verified email은 정규화하고 identity key는 이메일이 아닌 Kakao `sub`를 사용한다.
9. client ID/secret은 환경변수 필수이며 저장소 기본값은 없다.
10. 등록되지 않은 Provider 시작 경로는 OAuth 필터 진입 전에 HTTP 404로 종료한다.

조건: Java 21, Spring Boot 3.5.11, Spring Security 6.5.8, mock OIDC principal,
로컬 RSA 2048-bit key. 외부 Kakao 계정 및 운영 DB 쓰기는 수행하지 않는다.

## 5. Before / After

| 구분 | Before | After |
|---|---|---|
| Kakao Provider | 예약 ID만 존재 | OIDC adapter 활성 |
| OIDC 사용자 처리 | 없음 | 검증 후 공통 identity upsert |
| 토큰 검증 계약 | 미평가 | RS256/issuer/audience/expiration/nonce 평가 |
| 이메일 없음/미검증 | synthetic email로 ACTIVE 생성 가능 | 신규 Kakao 계정 DB write 전 차단 |
| 설정 | Kakao registration 없음 | 자격증명 없는 profile-gated 설정 |
| 운영 비활성 진입 | 미측정 | 첫 배포 500 검출 후 가용성 필터로 404 |

## 6. 검증 명령과 결과

```powershell
.\gradlew.bat :auth-common:test --tests "kr.itsdev.auth.common.oauth.*" --rerun-tasks
.\gradlew.bat :auth-common:test --tests "kr.itsdev.auth.common.oauth.OAuth2ProviderAvailabilityFilterTest"
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.security.KakaoOidcConfigurationTest" `
  --tests "kr.itsdev.devjobcollector.security.service.JpaSocialUserUpsertServiceTest"
.\gradlew.bat test
git diff --check
```

- 신규 Kakao 평가셋: 16/16
- auth-common OAuth: 20/20, failures/errors/skipped 0
- 전체 Gradle: BUILD SUCCESSFUL, 494건 중 407 passed, 87 skipped
- skip 87건은 MySQL/Testcontainers 실행 조건이 없는 기존 통합 평가셋이다.
- whitespace 오류: 0건

## 7. 커밋·배포·운영 결과

- 구현 커밋: `3afec95`
  - Backend Actions `34840437639` 성공
  - Docker Actions `34840437597` 성공
- 운영 1차 점검: 기존 6개 회귀는 통과했으나 비활성 Kakao 시작 경로 HTTP 500 검출
- 보완 커밋: `bea9735`
  - 등록 ID를 OAuth redirect filter 전에 확인하고 미등록 ID를 HTTP 404로 종료
  - Backend Actions `34855626062` 성공
  - Docker Actions `34855625923` 성공
- 최종 운영 비파괴 smoke:
  - health·공개 검색 HTTP 200
  - 기본 LOCAL credential·무토큰 회원 API HTTP 401
  - Google/GitHub OAuth 시작과 HTTPS callback 계약 2/2
  - 비활성 Kakao 시작 경로 HTTP 404, 5xx 0건

## 8. 합격 기준 및 제한

신규 16/16, 위조 토큰 4/4 차단, nonce/state 생성 2/2, 미검증 신규 계정
3/3 차단, 비활성 진입 404 1/1, secret/token 노출 0건을 모두 충족했다.

실제 Kakao 로그인은 Kakao 앱에서 OIDC 사용 설정, 동의항목, redirect URI,
REST API key와 client secret을 준비한 후에만 활성화한다. 실제 callback smoke가
완료되기 전에는 `kakao` profile과 프론트 로그인 버튼을 운영에서 활성화하지 않는다.
이메일 입력·DJC 이메일 인증 onboarding UI는 P8-03 범위로 남아 있으므로 현재는
이메일 미제공 또는 미검증 신규 사용자를 fail-closed 처리한다.
