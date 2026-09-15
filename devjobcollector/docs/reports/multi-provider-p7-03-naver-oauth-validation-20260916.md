# Multi-Provider P7-03 Naver OAuth 검증

## 1. 범위

- 작업: P7-03 Naver OAuth adapter와 통합 설정
- 포함: `response.id` 기반 identity, 선택 profile 변환, 비정상 응답 fail-closed,
  `naver` profile 전용 설정, authorization code와 state 계약 검증
- 제외: 실제 Naver 자격증명·callback 등록, 운영 profile, 실제 계정 로그인,
  프론트 버튼, account link/unlink UI

## 2. 공식 계약

- Authorization: `https://nid.naver.com/oauth2.0/authorize`
- Token: `https://nid.naver.com/oauth2.0/token`
- User-info: `https://openapi.naver.com/v1/nid/me`
- 성공 result code: `00`
- identity key: `response.id` (애플리케이션별 회원 고유값)
- 선택 profile: `response.email`, `name`, `nickname`, `profile_image`
- 근거: [로그인 API](https://developers.naver.com/docs/login/api/api.md),
  [프로필 조회 API](https://developers.naver.com/docs/login/profile/profile.md)

## 3. KPI / OKR / 합격 기준

| 항목 | 목표 | 결과 | 판정 |
|---|---:|---:|---|
| Naver 관련 평가셋 | 10/10 | 10/10 | 통과 |
| 비정상 user-info 차단 | 100% | 3/3 | 통과 |
| Secret 기본값 | 0건 | 0건 | 통과 |
| 전체 회귀 failures/errors | 0건 | 0/0 | 통과 |

- OKR 연결: 5개 소셜 Provider 목표 중 4개 adapter를 활성화하고 Provider별 불변
  식별·CSRF 방어 계약을 자동 평가셋으로 고정한다.
- 합격 기준: KPI 전부 충족, 전체 회귀 실패 0, 자격증명 없는 운영 Naver profile 비활성.

## 4. 평가셋

조건: Java 21, Spring Boot 3.5.11, 외부 호출·운영 DB write 없는 mock/config 평가.

1. 정상 중첩 응답을 `NAVER + response.id`로 변환한다.
2. name 누락 시 nickname을 사용하고 선택 claim 누락을 허용한다.
3. 실패 result code를 차단한다.
4. 누락·타입 오류 response envelope를 차단한다.
5. application-scoped id 누락을 차단한다.
6. registry에서 Naver를 활성화하고 미구현 Apple을 차단한다.
7. profile 격리, Secret 무기본값, 공식 endpoint를 검증한다.
8. authorization code, 고유 state, 불필요한 scope/nonce 부재를 검증한다.

## 5. Before / After

| 구분 | Before | After |
|---|---|---|
| Provider | 예약 ID만 존재 | Naver OAuth2 adapter 활성 |
| 식별 | 변환 계약 없음 | `response.id` 고정 |
| profile | 중첩 응답 미지원 | 선택 claim 변환 |
| 오류 | 처리 계약 없음 | result/envelope/id fail-closed |
| 설정 | registration 없음 | `naver` profile 전용 설정 |

## 6. 검증 결과

```powershell
.\gradlew.bat :auth-common:test `
  --tests "kr.itsdev.auth.common.oauth.NaverOAuth2ProfileAdapterTest" `
  --tests "kr.itsdev.auth.common.oauth.OAuthProviderRegistryTest" `
  test --tests "kr.itsdev.devjobcollector.security.NaverOAuthConfigurationTest"
.\gradlew.bat test
git diff --check
```

- Naver 관련: 10/10, failures/errors 0
- 전체: 501건 중 414 passed, 87 기존 조건부 skip, failures/errors 0
- whitespace 오류: 0건

## 7. 운영 활성화 게이트

Naver Developer Center에 서비스·callback URL과 필요한 profile 권한을 등록하고
`NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`을 운영 Secret에 주입한 뒤 `prod,naver`를
활성화한다. 실제 로그인, 동일 이메일 `ACCOUNT_LINK_REQUIRED`, 오류 callback,
로그 내 token·Secret 비노출을 확인하기 전 프론트 버튼은 활성화하지 않는다.

## 8. 배포 및 운영 smoke

- 구현 커밋: `717ab35`
- Backend Actions: `35006050883` 성공
- Docker Actions: `35006050830` 성공
- 운영 smoke:
  - health: HTTP 200
  - 공고 검색: HTTP 200
  - 무토큰 회원 API: HTTP 401
  - Google/GitHub OAuth 시작: 2/2 HTTP 302
  - 비활성 Naver OAuth 시작: HTTP 404, 5xx 0건
- Actions의 Node.js 20 강제 전환 및 `setup-java@v4` deprecation 경고는 별도
  workflow 유지보수 항목이며 이번 배포 결과에는 영향을 주지 않았다.
