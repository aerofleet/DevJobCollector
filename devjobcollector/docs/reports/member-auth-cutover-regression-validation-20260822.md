# 회원 인증 컷오버 회귀·운영 Smoke 검증 (2026-08-22)

## 결과

P2의 LOCAL/Google/GitHub 인증 경로를 하나의 MySQL 26.7 회귀 게이트로 묶었다.
기존 migration·repository·가입·social identity 평가셋에 컷오버 통합 테스트 7건을
추가했으며, DB 조건부 테스트를 포함한 전체 Gradle 평가셋은 26 suites, 84 tests에서
failure/error/skip 0건으로 통과했다.

운영 배포 후 계정을 생성하거나 DB를 변경하지 않고 실행할 수 있는 smoke 스크립트도
추가했다. 운영 배포와 실제 Google/GitHub 브라우저 로그인은 이번 작업에서 수행하지
않았다.

## 구현 범위

- ACTIVE LOCAL 사용자의 DB password hash 로그인 성공
- PENDING_EMAIL LOCAL 사용자의 올바른 비밀번호 로그인 차단
- 기존 Google identity의 `provider + provider_subject` 로그인 성공
- 기존 GitHub identity의 `provider + provider_subject` 로그인 성공
- 비활성 social identity 로그인과 `last_login_at` 변경 차단
- OAuth 성공 콜백의 access token 발급 및 허용된 프론트 URI 리다이렉트
- OAuth 실패 콜백의 `ACCOUNT_LINK_REQUIRED|OAUTH_LOGIN_FAILED` 고정 코드 전달
- MySQL 26.7 CI 평가 스크립트에 컷오버 테스트 추가
- health, 공개 검색, 구 기본 credential 거부, Google/GitHub OAuth 진입,
  선택적 ACTIVE LOCAL 로그인을 확인하는 비파괴 운영 smoke 추가

## KPI / OKR

| 항목 | 목표 | 결과 |
|---|---:|---:|
| LOCAL/Google/GitHub 컷오버 통합 평가셋 | 7/7 | 7/7 |
| MySQL 26.7 전체 백엔드 회귀 | 오류·skip 0건 | 84/84, 오류·skip 0건 |
| 기존 identity 로그인 성공률 | 100% | Google/GitHub 2/2 |
| 비활성 계정 인증 차단률 | 100% | LOCAL/social 2/2 |
| OAuth 콜백 비허용 상세 노출 | 0건 | 0건 |
| 운영 smoke의 DB 쓰기 요청 | 0건 | 0건 |

- OKR 연결: V3 identity 전환 후 기존 개인회원의 로그인 성공률을 유지하고,
  Account Takeover 방지 계약을 보존한 상태로 G1 운영 배포 판단 근거를 확보한다.

## 평가셋

### MySQL 26.7 컷오버 통합 테스트

`MemberAuthCutoverRegressionIntegrationTest` 7건:

1. ACTIVE LOCAL DB credential 성공
2. PENDING_EMAIL LOCAL 차단
3. 기존 Google identity 성공
4. 기존 GitHub identity 성공
5. PENDING_EMAIL social identity 차단 및 상태 불변
6. OAuth 성공 callback/token redirect 계약
7. OAuth 실패 callback/error 축약 계약

검증 명령:

```text
<GIT_BASH> ops/db/run-member-migration-tests.sh
```

- DB: MySQL `26.7.0`
- P2 핵심 평가셋: 35/35
- 실행 시간: 53초

### 전체 회귀

```text
set DJC_MIGRATION_TEST_URL=jdbc:mysql://127.0.0.1:<LOCAL_TEST_PORT>/devjob
set DJC_MIGRATION_TEST_USERNAME=<TEST_USER>
set DJC_MIGRATION_TEST_PASSWORD=<TEST_PASSWORD>
set DJC_MIGRATION_TEST_EXPECTED_VERSION=26.7.0
gradlew.bat cleanTest test --no-daemon
```

- 26 suites, 84 tests
- failures 0, errors 0, skipped 0
- 실행 시간: 57초

### 운영 smoke

배포 후 로컬 운영 단말에서 실행한다. 값과 출력에는 실제 비밀번호나 access token을
기록하지 않는다.

```text
export DJC_API_BASE_URL=https://<API_DOMAIN>
export DJC_EXPECTED_FRONTEND_ORIGIN=https://<FRONTEND_DOMAIN>
bash ops/auth/smoke-member-auth.sh
```

ACTIVE LOCAL 테스트 계정이 준비된 경우에만 아래 환경변수를 추가한다.

```text
export DJC_SMOKE_LOCAL_EMAIL=<SMOKE_LOCAL_EMAIL>
export DJC_SMOKE_LOCAL_PASSWORD=<SMOKE_LOCAL_PASSWORD>
bash ops/auth/smoke-member-auth.sh
```

자동 smoke 합격 기준:

- `/actuator/health`: HTTP 200, `status=UP`
- 공개 검색 API: HTTP 200
- 과거 기본 credential: HTTP 401
- Google OAuth 진입: HTTP 302, Google authorization endpoint로 이동
- GitHub OAuth 진입: HTTP 302, GitHub authorization endpoint로 이동
- 선택적 ACTIVE LOCAL 로그인: HTTP 200, 비어 있지 않은 access token, `Bearer`

브라우저 세션과 Provider 동의가 필요한 다음 3건은 G1 배포 후 수동으로 수행한다.

1. 기존 Google identity 로그인 후 `<FRONTEND_DOMAIN>/oauth/callback` 성공 화면
2. 기존 GitHub identity 로그인 후 동일 callback 성공 화면
3. 기존 LOCAL 이메일과 충돌하는 별도 social 계정 로그인 후
   `error=ACCOUNT_LINK_REQUIRED` 안내, 기존 계정 데이터 변경 0건

## Before / After

| 구분 | Before | After |
|---|---|---|
| P2 로그인 게이트 | 기능별 테스트에 분산 | 7건 컷오버 통합 평가셋으로 고정 |
| 기존 Provider 범위 | 개별 Google/GitHub 사례 | 두 Provider 기존 identity 성공을 동일 계약으로 검증 |
| 비활성 차단 | LOCAL/social 개별 확인 | P2 회귀 게이트에 함께 포함 |
| 운영 smoke | 명령 미확정 | 비파괴 자동 5종 + 선택적 LOCAL + 수동 OAuth 3종 |
| 전체 평가셋 | 77 tests | 84 tests, failure/error/skip 0 |

## 합격 기준

- MySQL 26.7 컷오버 평가셋 7/7
- 전체 Gradle 회귀 failure/error/skip 0건
- Google/GitHub 기존 identity 로그인 성공률 100%
- LOCAL/social 비활성 계정 차단률 100%
- OAuth 실패 상세 노출 0건
- V1/V2/V3 변경 0건
- 운영 smoke가 회원/identity/consent/profile 데이터를 쓰지 않을 것

코드 및 로컬 평가 기준은 모두 충족했다.

## G1 전 잔여 조건

- 정책 소유자가 실제 약관 버전을 확정하고 운영에
  `AUTH_SIGNUP_TERMS_POLICY_VERSION`, `AUTH_SIGNUP_PRIVACY_POLICY_VERSION`을 명시해야 한다.
- 기존 Google/GitHub/ACTIVE LOCAL smoke 계정을 준비하되 자격증명을 저장소나 결과
  문서에 기록하지 않는다.
- G1 배포 후 자동 smoke와 수동 OAuth 3건을 모두 통과하기 전 24시간 관찰을 시작하지
  않는다.
- 이번 작업에서는 운영 배포, 운영 DB 변경, 신규 Provider 구현을 수행하지 않았다.
