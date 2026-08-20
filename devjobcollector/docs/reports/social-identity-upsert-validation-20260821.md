# Social identity upsert 전환 검증 (2026-08-21)

## 결과

Google/GitHub 로그인 사용자 식별 기준을 `users.provider_user_id`에서
`user_identities(provider, provider_subject)`로 전환했다. 신규 소셜 사용자는 레거시
`provider_user_id`를 기록하지 않으며, 기존 identity 소유자가 `ACTIVE`가 아니면 인증을
거부한다.

운영 실측 버전과 같은 계열인 MySQL 26.7.0에서 관련 통합 평가셋 28/28이 통과했고,
전체 회귀 테스트도 실패 0건으로 통과했다. 배포와 운영 smoke는 수행하지 않았다.

## 구현 범위

- Google: issuer와 `email_verified`를 표준 `SocialProfile`에 전달
- GitHub: 안정적인 provider subject를 identity 키로 사용
- 기존 identity: `(provider, provider_subject)`로 사용자 조회 후 provider 메타데이터와
  `last_login_at` 갱신
- 신규 identity: 사용자, identity, 개인 profile을 단일 트랜잭션으로 생성
- 비활성 identity 소유자: `401 INVALID_CREDENTIALS`로 차단하고 상태를 변경하지 않음
- 신규 소셜 사용자: `users.provider_user_id = NULL` 유지

## 발견 사항과 조치

최초 구현은 신규 subject를 레거시 `users.provider_user_id`에도 기록했다. 해당 컬럼의
기존 unique collation이 대소문자를 구분하지 않아 `Case-Subject`와 `case-subject`를 서로
다른 identity로 저장할 수 없었다. 신규 쓰기를 binary collation이 적용된
`user_identities.provider_subject`로 일원화해 이 충돌을 제거했다.

또한 기존 identity가 `PENDING_EMAIL`인 경우 로그인 처리 중 활성 상태로 취급될 가능성을
확인했다. 조회 직후 `ACTIVE` 상태를 강제하고, 거부 시 `last_login_at`과 profile이 변경되지
않는 테스트를 추가했다.

## KPI / OKR

| 항목 | 목표 | 결과 |
|---|---:|---:|
| provider + subject 기존 identity 조회 성공률 | 100% | 1/1 (100%) |
| subject 대소문자 구분률 | 100% | 2개 identity 모두 보존 (100%) |
| subject 누락 쓰기 차단률 | 100% | 1/1, 생성 데이터 0건 |
| 비활성 identity 인증 차단률 | 100% | 1/1, 상태 변경 0건 |
| 신규 레거시 `provider_user_id` 쓰기 | 0건 | 0건 |
| MySQL 26.7 관련 통합 평가셋 | 100% 통과 | 28/28 |

- OKR 연결: 외부 identity의 불변 식별자를 단일 source of truth로 사용해 계정 오인식과
  상태 우회 가능성을 제거하고, Multi-Provider 확장의 P0 기반을 완성한다.

## 평가셋과 검증 명령

평가셋은 소셜 identity 6건, 개인가입 트랜잭션 6건, V3 migration 6건, V3 audit 5건,
repository 5건으로 총 28건이다. Google/GitHub 신규·기존 사용자, subject 대소문자,
subject 누락, 레거시 이메일 충돌, 비활성 계정을 포함한다.

```text
gradlew.bat cleanTest test \
  --tests kr.itsdev.devjobcollector.security.service.SocialIdentityUpsertIntegrationTest \
  --tests kr.itsdev.devjobcollector.security.signup.PersonalSignupTransactionIntegrationTest \
  --tests kr.itsdev.devjobcollector.migration.MemberV3MigrationTest \
  --tests kr.itsdev.devjobcollector.migration.MemberV3AuditTest \
  --tests kr.itsdev.devjobcollector.security.account.MemberFoundationRepositoryTest \
  --no-daemon
```

- MySQL: `26.7.0`
- 결과: 28 tests, failures 0, errors 0, skipped 0, `BUILD SUCCESSFUL` (50초)

```text
gradlew.bat clean test --no-daemon
```

- 결과: 총 73 tests, failures 0, errors 0
- 일반 회귀 실행 45건 통과, DB 환경 조건부 28건 skipped
- 위 조건부 28건은 동일 변경에서 MySQL 26.7 전용 실행으로 28/28 통과

## Before / After

| 구분 | Before | After |
|---|---|---|
| 소셜 사용자 조회 키 | 레거시 provider 컬럼 또는 메모리 upsert | `user_identities(provider, provider_subject)` |
| subject collation | 레거시 컬럼에서 대소문자 충돌 가능 | `utf8mb4_bin` identity 키로 구분 |
| 신규 레거시 subject 기록 | 기록 가능 | 0건 |
| 비활성 identity 로그인 | 명시적 서비스 차단 없음 | 100% 차단 |

## 합격 기준

- MySQL 26.7 관련 평가셋 28/28 통과
- 전체 회귀 failures/errors 0건
- subject 대소문자 구분 및 누락 차단률 100%
- 비활성 identity 상태 변경 0건
- V1/V2/V3 migration 변경 0건

모든 기준을 충족했다.

## 잔여 위험과 다음 작업

- P2-03 전환을 위해 동일 이메일의 활성 LOCAL 사용자를 자동 연결하는 호환 경로를
  격리해 두었다. 현재 상태로 배포하지 않으며 다음 작업에서
  `ACCOUNT_LINK_REQUIRED`로 교체한다.
- 예외 응답의 안정적인 에러 body 계약은 P2-03/P2-04에서 확정한다.
- 소셜 신규가입의 필수 동의 수집 경로는 아직 연결되지 않았다. 인증 회귀 범위와 함께
  별도 가입/온보딩 게이트로 보완해야 한다.
- 운영 DB 연결, 배포, 운영 smoke는 이번 로컬 검증 범위에 포함하지 않았다.
