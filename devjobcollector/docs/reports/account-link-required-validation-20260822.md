# 동일 이메일 소셜 계정 충돌 차단 검증 (2026-08-22)

## 결과

Google/GitHub의 미등록 provider subject가 기존 사용자 이메일과 충돌하면 기존 계정에
자동 연결하던 경로를 제거했다. 충돌 요청은 DB 변경 전에
`ACCOUNT_LINK_REQUIRED`로 종료하며, OAuth 브라우저 흐름은 프론트 콜백으로 해당 코드를
전달한다. 일반 API 계약은 HTTP 409와 고정 오류 본문을 사용한다.

MySQL 26.7.0에서 전체 백엔드 평가셋 77/77이 실패·오류·skip 없이 통과했다. 프론트
lint와 production build도 통과했다. 운영 배포와 smoke는 수행하지 않았다.

## 구현 범위

- 기존 이메일 자동 조회 후 social identity를 추가하던 호환 경로 제거
- 대소문자를 무시한 기존 이메일 충돌을 DB 쓰기 전에 차단
- OAuth 오류 코드 `ACCOUNT_LINK_REQUIRED`와 기타 오류 `OAUTH_LOGIN_FAILED` 분리
- OAuth 예외 상세를 프론트 URL에 노출하지 않는 실패 핸들러 추가
- HTTP 409 오류 본문에 `status`, `code`, `message`, `path` 고정 필드 제공
- 로그인 콜백에서 계정 재인증 안내 표시
- 성공/실패 콜백 URI를 환경변수로 분리 가능하게 구성

## KPI / OKR

| 항목 | 목표 | 결과 |
|---|---:|---:|
| 동일 이메일 충돌 자동 연결 | 0건 | 0/20건 |
| `ACCOUNT_LINK_REQUIRED` 반환률 | 100% | 20/20건 (100%) |
| 충돌 시 기존 user/identity/profile 상태 변경 | 0건 | 0건 |
| MySQL 26.7 전체 백엔드 평가셋 | 100% | 77/77, skip 0 |
| 프론트 정적 검증 | 오류 0건 | lint/build 오류 0건 |
| 예상치 못한 OAuth 예외 상세 노출 | 0건 | 0건 |

- OKR 연결: 외부 Provider 이메일만으로 기존 계정을 병합하지 않도록 하여 Account
  Takeover 위험을 차단하고, 안전한 Multi-Provider 인증 확장 게이트를 완성한다.

## 평가셋과 검증 명령

### 충돌 평가셋

- 기존 ACTIVE LOCAL 사용자 20건
- Google 미등록 subject 20건
- Provider email은 기존 이메일의 대문자 변형으로 입력
- 기대값: 자동 연결 0건, 오류 코드 일치 20건, 신규 social identity 0건,
  기존 identity/profile 변경 0건

### MySQL 26.7 전체 회귀

```text
set DJC_MIGRATION_TEST_URL=jdbc:mysql://127.0.0.1:<LOCAL_TEST_PORT>/devjob
set DJC_MIGRATION_TEST_USERNAME=<TEST_USER>
set DJC_MIGRATION_TEST_PASSWORD=<TEST_PASSWORD>
set DJC_MIGRATION_TEST_EXPECTED_VERSION=26.7.0
gradlew.bat cleanTest test --no-daemon
```

- DB 서버: MySQL `26.7.0`
- 결과: 25 suites, 77 tests, failures 0, errors 0, skipped 0
- 실행 시간: 1분 3초

### 프론트 검증

```text
npm.cmd run lint
npm.cmd run build
```

- ESLint 오류 0건
- Vite production build 성공, 1,814 modules transformed

## Before / After

| 구분 | Before | After |
|---|---|---|
| 미등록 subject + 기존 email | 기존 user에 social identity 자동 추가 | DB 변경 전 `ACCOUNT_LINK_REQUIRED` |
| OAuth 실패 UX | Spring 기본 실패 경로 | 허용된 고정 코드로 프론트 콜백 이동 |
| API 오류 계약 | reason 기반, 고정 code 필드 없음 | HTTP 409 + 고정 `code` 필드 |
| 예외 상세 노출 | 기본 처리 의존 | 예상치 못한 상세는 일반 코드로 축약 |

## 합격 기준

- 동일 이메일 충돌 20건 중 자동 연결 0건
- `ACCOUNT_LINK_REQUIRED` 20/20건
- 충돌 전후 기존 user/identity/profile 변경 0건
- MySQL 26.7 전체 테스트 failure/error/skip 0건
- 프론트 lint/build 오류 0건
- V1/V2/V3 migration 변경 0건

모든 기준을 충족했다.

## 잔여 위험과 다음 작업

- P8의 실제 account link/unlink 및 재인증 API는 아직 구현하지 않았다. 프론트는 현재
  재인증 필요 안내만 제공한다.
- P2-04에서 LOCAL/Google/GitHub 전체 로그인 회귀와 운영 smoke 명령을 확정한다.
- 운영 배포 전 약관 버전 환경변수의 정책 소유자 확인이 필요하다.
- P2-04와 G1 전에는 신규 Provider를 추가하거나 운영 배포하지 않는다.
