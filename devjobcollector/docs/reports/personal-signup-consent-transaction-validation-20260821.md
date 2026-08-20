# 개인가입 consent/identity/profile 트랜잭션 검증 보고서

- 작업: P2-01 개인가입 immutable consent 및 profile 연결
- 검증일: 2026-08-21 KST
- 대상 DB: MySQL 26.7.0 (운영 런타임 `26.7.0-cloud`와 동일 계열)
- 운영 DB 주소: `<DB_PRIVATE_IP>:3306`
- 배포: 미수행

## 결과

개인가입과 이메일 인증 흐름을 V3 member foundation에 연결했다.

- 가입 transaction: `users` 1건 + LOCAL `user_identities` 1건 + 필수 `user_consents` 2건 + `email_verification_tokens` 1건
- 필수 consent: `TERMS_OF_SERVICE`, `PRIVACY_POLICY`를 같은 발생시각의 `ACCEPTED` 이벤트로 append
- 정책 버전: 서버 설정 `AUTH_SIGNUP_TERMS_POLICY_VERSION`, `AUTH_SIGNUP_PRIVACY_POLICY_VERSION`에서 결정
- 이메일 인증 transaction: user ACTIVE + LOCAL identity email verified + `personal_profiles` 1건
- 필수 동의 누락: DB 쓰기 전 HTTP 400 `CONSENT_REQUIRED`
- 메일 전송: DB commit 이후 `AFTER_COMMIT` listener에서 실행하며 실패해도 가입 데이터를 rollback하지 않음

V1/V2/V3 migration 파일은 수정하지 않았다.

## 구현 결정

정책 버전은 클라이언트가 보내지 않고 서버 설정만 사용한다. 설정 클래스는 blank와 50자 초과 값을 시작 단계에서 거부한다. 저장소에 실제 약관 문서 버전 근거가 없으므로 기본값은 초기 호환값 `v1`이며, 운영 배포 전 정책 소유자가 실제 버전을 확인해 두 환경변수를 명시해야 한다.

인증 메일 이벤트는 이메일과 일회성 코드를 메모리에서 전달하지만 오류 로그에는 두 값을 포함하지 않는다. 메일 실패 시 사용자는 기존 resend API로 재시도할 수 있다.

## KPI / OKR / 평가셋

- 목표 KPI: 정상 가입 원자성 100%, 필수 consent 누락 차단률 100%, 중복 가입 추가 쓰기 0건, transaction rollback 잔존행 0건, 메일 실패 시 가입 데이터 보존률 100%, 이메일 인증 후 profile/identity 전환률 100%.
- OKR 연결: P2 identity cutover에서 신규 LOCAL 계정의 identity 누락과 consent 감사 누락을 0건으로 만들고 이메일 인증 장애와 DB 원자성을 분리한다.
- 평가셋: 정상 가입, 이메일 인증, 필수 consent 누락, 중복 email, 잘못된 서버 정책 버전 rollback, commit 이후 메일 실패 총 6종. 기존 migration 6건, audit 5건, repository 5건을 함께 실행했다.
- Before: 가입은 `users + token`만 저장하고 consent/identity/profile을 영속화하지 않았으며 SMTP가 DB transaction 안에서 실행됐다.
- After: 가입 시 `1 user + 1 identity + 2 consents + 1 token`, 인증 후 `1 profile`, 메일은 AFTER_COMMIT 경계로 분리됐다.
- 합격 기준: MySQL 26.7 관련 테스트 22/22, skipped/failure/error 0, 전체 Gradle 회귀 성공, V1/V2/V3 변경 0, secret/code 원문 로그 0.

## 검증 명령 및 결과

```text
set DJC_MIGRATION_TEST_URL=jdbc:mysql://127.0.0.1:<LOCAL_TEST_PORT>/devjob
set DJC_MIGRATION_TEST_USERNAME=<TEST_USER>
set DJC_MIGRATION_TEST_PASSWORD=<TEST_PASSWORD>
set DJC_MIGRATION_TEST_EXPECTED_VERSION=26.7.0
gradlew.bat cleanTest test \
  --tests kr.itsdev.devjobcollector.migration.MemberV3MigrationTest \
  --tests kr.itsdev.devjobcollector.migration.MemberV3AuditTest \
  --tests kr.itsdev.devjobcollector.security.account.MemberFoundationRepositoryTest \
  --tests kr.itsdev.devjobcollector.security.signup.PersonalSignupTransactionIntegrationTest \
  --no-daemon
```

- `PersonalSignupTransactionIntegrationTest`: 6 tests, skipped 0, failures 0, errors 0
- `MemberV3MigrationTest`: 6 tests, skipped 0, failures 0, errors 0
- `MemberV3AuditTest`: 5 tests, skipped 0, failures 0, errors 0
- `MemberFoundationRepositoryTest`: 5 tests, skipped 0, failures 0, errors 0
- 합계: 22/22, `BUILD SUCCESSFUL in 46s`

```text
gradlew.bat clean test --no-daemon
```

- 최종 결과: `BUILD SUCCESSFUL in 38s`

## 잔여 위험 및 다음 작업

- 운영 배포 전에 이용약관·개인정보 처리방침의 실제 버전을 확인하고 두 policy version 환경변수를 설정해야 한다. 확인 전 배포하지 않는다.
- `ResponseStatusException`의 reason은 `CONSENT_REQUIRED`지만 공통 stable error response body는 아직 완성되지 않았다. P2-03의 `ACCOUNT_LINK_REQUIRED` 계약과 함께 공통 오류 매핑을 적용한다.
- 동일 email 동시 가입 race는 DB unique constraint가 막지만 stable 409 mapping과 concurrency 20회 평가는 P2-03/P2-04에서 수행한다.
- 다음 작업 P2-02에서 Google/GitHub 조회와 upsert를 `provider + provider_subject` 기준으로 전환한다.
