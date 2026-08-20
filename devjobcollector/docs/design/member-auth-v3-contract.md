# DJC V3 Member Identity / Consent Contract

> 상태: Accepted for P1 implementation
> 결정일: 2026-08-20
> 범위: P0-02 — V3 컬럼, 상태, 오류 코드, LOCAL credential 위치

## 1. 우선순위와 근거

이 계약은 다음 순서로 해석한다.

1. `DJC Multi-Provider Authentication Architecture v1.1`
2. `DJC 개인·기업 회원 도메인 및 계정 시스템 설계 명세 v1.0`
3. 현재 V2 스키마와 코드

두 Notion 문서가 충돌하는 `user_identities` 도입 시점과 컬럼은 최신 Authentication v1.1을 따른다. 따라서 V3에 `personal_profiles`, `user_consents`, `user_identities`를 함께 만들되, v1.0의 `user_identities.password_hash`는 채택하지 않는다.

## 2. 확정 결정

| ID | 결정 |
|---|---|
| V3-ADR-01 | `users`는 인증 주체이며 개인/기업 유형 컬럼을 추가하지 않는다. |
| V3-ADR-02 | `user_identities`는 V3에 포함하고 `provider + provider_subject`를 인증수단 식별키로 사용한다. |
| V3-ADR-03 | V3에서 `users.provider`, `users.provider_user_id`, `users.password_hash`를 삭제하지 않는다. |
| V3-ADR-04 | LOCAL password hash의 P1/P2 source of truth는 `users.password_hash`로 유지한다. `user_identities`에는 secret을 저장하지 않는다. |
| V3-ADR-05 | LOCAL `provider_subject`는 정규화된 `users.email`이다. 이메일 변경은 `users.email`과 LOCAL identity subject를 한 트랜잭션에서 갱신한다. |
| V3-ADR-06 | 소셜 이메일 충돌은 자동 연결하지 않고 `409 ACCOUNT_LINK_REQUIRED`로 종료한다. |
| V3-ADR-07 | 기존 consent를 추정해 backfill하지 않는다. 신규 동의만 서버 설정의 policy version으로 append한다. |
| V3-ADR-08 | 개인 profile은 이메일 인증 완료 트랜잭션에서 생성한다. 이메일이 검증된 소셜 신규 가입은 계정 생성 트랜잭션에서 생성한다. |
| V3-ADR-09 | 설정 기반 `auth.local-login.users`는 회원 identity가 아니다. 운영 기본값을 비활성화하고 기본 자격증명을 제거한다. |
| V3-ADR-10 | DB native ENUM을 사용하지 않고 애플리케이션 enum + `VARCHAR`로 관리한다. |

## 3. V3 데이터 사전

### 3.1 personal_profiles

| 컬럼 | 타입 | NULL | 기본값 | 제약/의미 |
|---|---|---:|---|---|
| `user_id` | `BIGINT` | N | - | PK, FK → `users.id`, `ON DELETE CASCADE` |
| `profile_status` | `VARCHAR(30)` | N | `ACTIVE` | `ACTIVE|PRIVATE|DELETED` |
| `created_at` | `DATETIME(6)` | N | `CURRENT_TIMESTAMP(6)` | 생성시각 |
| `updated_at` | `DATETIME(6)` | N | current + on update | 수정시각 |

`users.name`과 중복되는 `display_name`은 V3에 만들지 않는다.

### 3.2 user_consents

| 컬럼 | 타입 | NULL | 기본값 | 제약/의미 |
|---|---|---:|---|---|
| `id` | `BIGINT` | N | auto | PK |
| `user_id` | `BIGINT` | N | - | FK → `users.id`, `ON DELETE RESTRICT` |
| `consent_type` | `VARCHAR(50)` | N | - | consent 종류 |
| `policy_version` | `VARCHAR(50)` | N | - | 서버가 결정한 정책 버전 |
| `action` | `VARCHAR(20)` | N | - | `ACCEPTED|REVOKED` |
| `occurred_at` | `DATETIME(6)` | N | - | 사용자 행위 발생시각 |
| `created_at` | `DATETIME(6)` | N | `CURRENT_TIMESTAMP(6)` | DB 기록시각 |

인덱스는 `idx_user_consents_user_timeline(user_id, consent_type, policy_version, occurred_at, id)` 하나로 시작한다. append-only Entity에는 상태 변경 메서드를 두지 않으며 ConsentService는 insert만 수행한다.

V3 consent type은 `TERMS_OF_SERVICE`, `PRIVACY_POLICY`, `MARKETING`, `PERSONAL_DATA_COLLECTION`을 예약한다. 현재 개인가입은 필수 `TERMS_OF_SERVICE`, `PRIVACY_POLICY` 두 건만 기록한다. 정책 버전은 클라이언트 입력을 신뢰하지 않고 서버 설정에서 가져온다.

### 3.3 user_identities

| 컬럼 | 타입 | NULL | 기본값 | 제약/의미 |
|---|---|---:|---|---|
| `id` | `BIGINT` | N | auto | PK |
| `user_id` | `BIGINT` | N | - | FK → `users.id`, `ON DELETE CASCADE` |
| `provider` | `VARCHAR(30)` | N | - | `LOCAL|GOOGLE|GITHUB|KAKAO|NAVER|APPLE` |
| `provider_subject` | `VARCHAR(255)` | N | - | provider의 불변 식별자, `utf8mb4_bin` 비교 |
| `issuer` | `VARCHAR(255)` | Y | - | OIDC issuer, exact value |
| `provider_email` | `VARCHAR(255)` | Y | - | provider가 마지막으로 제공한 이메일 |
| `provider_email_verified` | `BOOLEAN` | Y | - | provider가 명시한 검증 여부; 모르면 NULL |
| `last_login_at` | `DATETIME(6)` | Y | - | 마지막 성공 로그인 |
| `created_at` | `DATETIME(6)` | N | current | 생성시각 |
| `updated_at` | `DATETIME(6)` | N | current + on update | 수정시각 |

필수 제약:

- `uk_user_identities_provider_subject(provider, provider_subject)`
- `uk_user_identities_user_provider(user_id, provider)`
- `idx_user_identities_user_id(user_id)`는 두 번째 unique index의 left prefix로 충족되므로 별도 생성하지 않는다.

`provider_subject`는 외부 opaque ID의 대소문자를 보존하기 위해 binary collation을 사용한다. 이메일은 외부 identity key로 사용하지 않는다. 예외적으로 LOCAL만 명시된 계약에 따라 정규화 이메일을 subject로 사용한다.

## 4. 상태 계약

| 영역 | 값 | 전이/규칙 |
|---|---|---|
| `users.status` | `PENDING_EMAIL` | LOCAL 가입 직후. 로그인 불가 |
|  | `ACTIVE` | 이메일 인증 또는 검증된 소셜 onboarding 완료 |
|  | `SUSPENDED` | 운영 정지. 모든 identity 로그인 불가 |
|  | `WITHDRAWN` | 탈퇴 상태. 모든 identity 로그인 불가 |
| `personal_profiles.profile_status` | `ACTIVE` | 기본 상태 |
|  | `PRIVATE` | 외부 공개 제한 |
|  | `DELETED` | soft delete 상태 |
| consent action | `ACCEPTED`, `REVOKED` | 기존 행 update 없이 새 이벤트 append |

V3에서는 identity별 status를 추가하지 않는다. 연결 해제는 행 삭제로 표현하고, 계정 status는 `users.status`에서 일관되게 판단한다. 마지막 identity 삭제 방지는 서비스 invariant다.

## 5. LOCAL credential 계약

### P1/P2 전환 기간

- `users.password_hash`가 유일한 LOCAL password hash source of truth다.
- `user_identities`의 LOCAL 행은 로그인 수단의 존재와 subject만 나타낸다.
- 로그인 조회 순서는 LOCAL identity → user → `users.password_hash` 검증으로 전환한다.
- 이중 쓰기 password hash나 `user_identities.password_hash`를 만들지 않는다.

### 후속 분리

P2/G1 안정화 후 별도 ADR에서 `local_credentials(identity_id PK/FK, password_hash, password_changed_at, failed_attempts, locked_until)` 분리를 검토한다. 이때도 credential lifecycle이 다른 소셜 metadata와 섞이지 않도록 `user_identities` 자체에는 hash를 넣지 않는다.

### 설정 기반 fallback

현재 `AuthLocalLoginProperties`는 기본 활성화이며 기본 username/password까지 가진다. 이는 운영 회원 source of truth와 충돌하고 고정 credential 노출 위험이 있으므로 다음을 P0 보안 게이트로 둔다.

1. `AUTH_LOCAL_LOGIN_ENABLED` 기본값을 `false`로 변경한다.
2. `application.yml`의 기본 username/email/password 값을 제거한다.
3. 운영 일반 로그인에서 DB 조회 실패 후 설정 계정 fallback을 수행하지 않는다.
4. break-glass 관리 계정이 필요하면 별도 인증 경로, 강한 secret, 감사 로그, 만료 절차를 설계한다.

## 6. V2 → V3 backfill 계약

운영 감사 시점의 `users`는 0건이지만 migration은 일반적인 V2 데이터도 처리해야 한다.

1. `users.provider`가 `LOCAL|GOOGLE|GITHUB` 외 값이면 migration을 실패시킨다.
2. LOCAL은 `LOWER(TRIM(users.email))`을 subject로 사용한다.
3. Google/GitHub는 `users.provider_user_id`를 subject로 사용하며 NULL/blank이면 실패시킨다.
4. legacy Google/GitHub의 `provider_email_verified`는 검증 증거가 없으므로 NULL로 둔다.
5. `users.status=ACTIVE`인 기존 사용자만 `personal_profiles(ACTIVE)`를 생성한다.
6. 기존 request boolean만으로 정책 버전과 동의시각을 증명할 수 없으므로 consent 행을 만들지 않는다.
7. unique 위반, orphan, 누락 subject가 하나라도 있으면 V3를 성공 처리하지 않는다.
8. 기존 V2 인증 컬럼은 backfill 후에도 유지한다.

## 7. API 오류 계약

오류 응답의 최소 공통 형식은 다음과 같다.

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다.",
  "traceId": "optional-trace-id"
}
```

필드 오류는 선택적 `fieldErrors` 배열로 추가하고, account collision은 선택적 `provider`를 추가한다. 내부 예외명과 SQL 문구는 노출하지 않는다.

| HTTP | code | 적용 상황 |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | request/bean validation 실패 |
| 400 | `CONSENT_REQUIRED` | 필수 약관 미동의 |
| 400 | `EMAIL_VERIFICATION_INVALID` | 계정 또는 코드가 유효하지 않음 |
| 400 | `UNSUPPORTED_AUTH_PROVIDER` | 지원하지 않는 provider |
| 401 | `INVALID_CREDENTIALS` | LOCAL 로그인 실패; 계정 존재 여부를 구분하지 않음 |
| 409 | `EMAIL_ALREADY_EXISTS` | LOCAL 신규가입 email 충돌 |
| 409 | `EMAIL_ALREADY_VERIFIED` | 이미 인증된 계정의 인증/재발송 |
| 409 | `ACCOUNT_LINK_REQUIRED` | 미등록 social identity가 기존 email과 충돌 |
| 409 | `IDENTITY_ALREADY_LINKED` | provider identity가 이미 다른 user에 연결 |
| 409 | `LAST_IDENTITY_CANNOT_BE_REMOVED` | 마지막 로그인 수단 해제 |
| 410 | `EMAIL_VERIFICATION_EXPIRED` | 인증 코드 만료 |
| 429 | `EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED` | 인증 시도 초과 |
| 429 | `AUTH_RATE_LIMITED` | 가입/로그인/재발송 rate limit |

`ACCOUNT_LINK_REQUIRED`는 identity를 생성하거나 기존 user를 수정하지 않는다. 프론트는 기존 계정 재인증 후 별도 link flow로 이동한다.

## 8. 트랜잭션 경계

- LOCAL 가입 DB transaction: `users + LOCAL user_identity + 필수 user_consents + email_verification_token`.
- 메일 전송: commit 이후. 실패 시 가입 transaction을 rollback하지 않고 재발송 가능 상태를 유지한다.
- 이메일 인증 transaction: token 사용 + user ACTIVE + personal_profile 생성.
- 검증된 social 신규가입 transaction: users + social identity + consents + personal profile.
- social email 충돌: DB mutation 없이 `ACCOUNT_LINK_REQUIRED`.

## 9. 개인정보 분류와 로그 정책

| 데이터 | 분류 | 저장/노출 규칙 |
|---|---|---|
| `users.password_hash` | 인증 secret | DB와 password verifier에서만 사용, API/로그/artifact 금지 |
| `provider_subject` | 가명 식별자 | DB 저장 허용, 일반 로그·분석 이벤트 원문 금지 |
| `provider_email`, `users.email` | 개인정보 | 최소 조회, 응답 목적 제한, 로그에서는 마스킹 |
| consent type/version/action/time | 준법·감사 데이터 | 변경 금지, 관리자 권한 조회, 보존기간은 별도 ADR |
| verification code/hash, OAuth token, DJC JWT | 인증 secret | 코드 hash만 단기 저장, token 원문 장기 저장·로그 금지 |
| `last_login_at` | 보안 활동정보 | 계정 보안·감사 목적만 사용 |

운영 감사와 migration report에는 row-level email/subject/hash를 출력하지 않고 건수만 기록한다.

## 10. P1 migration 평가셋

| 분류 | fixture | 기대 결과 |
|---|---|---|
| clean | 운영과 동일한 26.7 계열의 빈 DB V1→V3 | 성공, 14개 base table (`flyway_schema_history` 포함) |
| production | V2 schema-only + 운영 분포 | 성공 |
| LOCAL | ACTIVE + password | identity/profile 생성 |
| LOCAL | PENDING_EMAIL + password | identity 생성, profile 없음 |
| LOCAL | password 없음 | migration은 구조 생성 성공, audit 실패로 cutover 차단 |
| social | Google/GitHub 정상 subject | identity 생성 |
| social | subject NULL/blank | migration 실패 |
| duplicate | provider+subject 중복 | migration 실패 |
| consent | legacy 사용자 | consent 0건; 허위 backfill 없음 |
| signup | 필수 동의 true | 정책별 ACCEPTED 2건 |
| collision | 동일 email social 20건 | 자동 link 0, 409 100% |
| concurrency | 동일 LOCAL email 20회, concurrency 10 | user 1, 나머지 409 |
| status | SUSPENDED/WITHDRAWN | 모든 identity 로그인 차단 |
| privacy | logs/artifacts | password/hash/token/subject 원문 0건 |

## 11. KPI / OKR / 합격 기준

- 목표 KPI: clean/V2 upgrade 성공률 100%, identity orphan/duplicate 0, 기존 로그인 회귀 성공률 100%, 자동 email linking 0건, 직접 secret 로그 0건.
- OKR 연결: 안전한 다중 인증 기반을 먼저 구축해 기업회원 MVP와 후속 Provider 확장의 계정 탈취 위험을 낮춘다.
- 평가셋: 위 migration fixture 14종, LOCAL 50건, Google/GitHub 각 20건, email collision 20건, concurrency 10.
- Before: 단일 provider 컬럼, email 자동 병합, consent 미저장, profile 없음, 설정 기반 기본 credential fallback.
- 목표 After: identity 1:N, stable error code, consent append-only, 인증 후 profile, fallback 기본 비활성.
- 합격 기준: migration/회귀 100%, orphan/duplicate/자동 link/secret 노출 0, 모든 상태·오류 mapping test 통과.

## 12. 명시적 보류

- `local_credentials` 물리 분리는 G1 이후 별도 ADR.
- account link/unlink API는 P8이지만 P2에서 `ACCOUNT_LINK_REQUIRED` 차단 계약은 먼저 적용.
- 정책 문구와 실제 version 값은 제품/법무 승인 후 서버 설정으로 주입.
- 기업 직접 공고 모델과 법적 보존 기간은 회원 V3 범위 밖의 별도 ADR.
