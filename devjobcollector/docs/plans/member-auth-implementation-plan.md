# DJC 회원·기업·Multi-Provider 인증 통합 실행계획

> 상태: In Progress — G1 운영 게이트 완료, Career Hub 수동 QA 후 P3 일정 재산정 대기
> 기준일: 2026-08-19
> 구현 시작 예정: 2026-08-20
> 산정 조건: 주 5일, 구현자 1명, Provider 자격증명과 검토자 응답이 예정대로 제공됨

## 1. Source of Truth

- Notion: [DJC 개인·기업 회원 도메인 및 계정 시스템 설계 명세](https://app.notion.com/p/3c19cc8ccb4a81a98eb9c9e7f958dca7)
- Notion: [DJC Multi-Provider Authentication Architecture v1.1](https://app.notion.com/p/3c19cc8ccb4a81478753db914b608266)
- Notion plan: [DJC 회원·기업·Multi-Provider 인증 통합 실행계획](https://app.notion.com/p/3c19cc8ccb4a817c85cafb737b06dacb)
- Notion log: [통합 실행계획 확정 기록](https://app.notion.com/p/3c19cc8ccb4a8125a124c2659a3c0275)
- Local design: `docs/design/member-domain-erd-strategy-draft.md`
- Runtime handover: `C:/Users/aerof/.codex/HANDOVER.md`

문서가 충돌하면 더 최근이며 인증 전환을 구체화한 Multi-Provider Authentication Architecture v1.1을 우선한다. 이에 따라 `user_identities`는 V6이 아니라 V3에 포함한다.

## 2. 현재 코드와 설계의 차이

- 운영 마이그레이션은 V1/V2뿐이며 `users`, `email_verification_tokens`가 존재한다.
- `users`가 `provider`, `provider_user_id`, `password_hash`를 직접 보유한다.
- Google/GitHub 로그인은 `provider + provider_user_id` 조회 후, 없으면 이메일로 기존 사용자를 찾아 자동 병합한다.
- 동일 이메일 자동 병합은 최신 인증 문서의 Account Takeover 방지 원칙과 충돌한다.
- 개인 약관 동의 이벤트와 개인 프로필이 영속화되지 않는다.
- 기업 도메인·가입·검증·membership authorization이 없다.
- OAuth 및 Flyway upgrade 전용 테스트가 부족하다.

## 3. 확정 결정

1. `users`는 공통 인증 주체이며 `account_type`을 추가하지 않는다.
2. 개인회원은 `personal_profiles`, 기업회원은 `company_members` 관계로 판정한다.
3. `user_identities`를 V3에 포함하고 LOCAL/Google/GitHub 기존 계정을 backfill한다.
4. 기존 `users` 인증 컬럼은 V3에서 삭제하지 않는다. 새 읽기 경로 검증 후 별도 마이그레이션에서 제거 여부를 결정한다.
5. 동일 이메일 소셜 계정은 자동 연결하지 않고 `ACCOUNT_LINK_REQUIRED`로 차단한다.
6. 약관 이력은 `ACCEPTED|REVOKED` append-only 이벤트로 기록한다.
7. 개인 프로필은 이메일 인증 완료 직후 생성한다.
8. 기업은 다중 소속을 허용하며 권한 source of truth는 `company_members`다.
9. 기업 검증 MVP는 사업자번호 hash + 증빙 + 관리자 수동 승인으로 구현한다.
10. Kakao/Naver/Apple은 기업 MVP 이후 확장한다. Google/GitHub를 먼저 새 identity 구조로 안정화한다.

## 4. 일정 및 진행 순서

| Phase | 기간 | 작업 | 산출물 | 종료 게이트 |
|---|---|---|---|---|
| P0 Baseline & Contract | 08-20~08-21 | 운영 V2 감사, 코드 baseline, 컬럼/상태/에러 계약 확정 | 감사 결과, 확정 DDL 초안, 테스트 매트릭스 | V2 실재 확인, 미결정 사항 0개 또는 명시적 보류 |
| P1 V3 Foundation | 08-24~08-28 | `personal_profiles`, `user_consents`, `user_identities`, backfill, Entity/Repository | Flyway V3, migration tests | clean/V2 upgrade 100%, orphan/duplicate 0 |
| P2 Identity Cutover | 08-31~09-04 | 개인가입 동의 트랜잭션, Google/GitHub identity 전환, 이메일 자동병합 제거 | LOCAL/Google/GitHub 새 인증 경로 | 회귀 테스트 100%, 기존 로그인 성공, 충돌은 409 |
| G1 Production Gate | 09-07~09-08 | 배포, smoke, 24시간 관찰 | V3 운영 적용 기록 | health/API 정상, 인증 오류율 기준 충족 |
| P3 Company Core | Career Hub CH-G1 이후 재산정 | Flyway V5, Company/Member 도메인, 기업 가입 Facade | 기업 생성 + OWNER membership | 원자적 생성, 중복/마지막 OWNER invariant 통과 |
| P4 Verification & Authorization | P3 완료 후 재산정 | Flyway V6, 수동 검증, 역할 권한 매트릭스 | 승인/반려 및 company authorization | 역할×기업상태 접근 차단률 100% |
| P5 Enterprise UI | 09-23~09-29 | 기업회원 탭, 가입·검증 상태·오류 UX | 기업 가입 E2E UI | 개인가입 회귀 0, 기업 E2E 핵심 흐름 통과 |
| P6 MVP Hardening | 09-30~10-05 | rate limit, audit, metrics, concurrency, migration rehearsal, 배포 | 기업회원 MVP | DoD/KPI 충족, rollback 절차 검증 |
| P7 Provider Expansion | 10-06~10-21 | Kakao, Naver, Apple adapter 및 통합 테스트 | 5개 소셜 Provider | provider별 식별·보안 평가셋 통과 |
| P8 Linking & Final Security | 10-22~10-29 | link/unlink, 마지막 identity 보호, state/nonce/PKCE 종합 점검 | 계정 연결 UI/API 및 보안 보고 | takeover/토큰 누출 평가셋 0건 |

기존 기업회원 MVP 목표일 **2026-10-05**와 전체 Multi-Provider 목표일 **2026-10-29**는 Career Hub 우선순위 변경으로 재산정 대상이다. CH-G1 완료 후 P3~P8 일정을 다시 확정하며, 외부 Provider 자격증명이나 Apple 설정이 늦으면 P7/P8만 별도 조정한다.

## 5. 1~2일 단위 작업 목록

### P0 — 먼저 수행

- [x] P0-01 운영 DB/Flyway V2 감사 및 익명화 스냅샷 준비 (완료: 2026-08-19, 결과: `docs/reports/member-foundation-production-audit-20260819.md`)
- [x] P0-02 V3 컬럼 사전·상태·에러 코드·LOCAL password 위치 확정 (완료: 2026-08-20, 계약: `docs/design/member-auth-v3-contract.md`)
- [x] P0-SEC-01 설정 기반 기본 LOCAL credential 비활성화 및 운영 회귀 검증 (완료: 2026-08-20, 결과: `docs/reports/local-login-default-credential-remediation-20260820.md`)

### P1 — V3 데이터 기반

- [x] P1-01 V3 DDL과 clean/V2 upgrade migration test 작성 (완료: 2026-08-20, MySQL 26.7 integration 6/6)
- [x] P1-02 UserIdentity/Consent/PersonalProfile Entity·Repository 작성 (완료: 2026-08-20, MySQL 26.7 관련 평가셋 16/16)
- [x] P1-03 기존 LOCAL/Google/GitHub backfill 검증과 duplicate audit 작성 (완료: 2026-08-20, MySQL 26.7 관련 평가셋 16/16)

### P2 — 현재 인증 경로 전환

- [x] P2-01 개인가입에 immutable consent와 profile 생성 연결 (완료: 2026-08-21, MySQL 26.7 관련 평가셋 22/22)
- [x] P2-02 Google/GitHub 조회를 `provider + provider_subject`로 전환 (완료: 2026-08-21, MySQL 26.7 관련 평가셋 28/28)
- [x] P2-03 동일 이메일 자동병합 제거 및 `ACCOUNT_LINK_REQUIRED` 계약 추가 (완료: 2026-08-22, MySQL 26.7 전체 평가셋 77/77)
- [x] P2-04 LOCAL/Google/GitHub 회귀·통합 테스트와 운영 smoke 작성 (완료: 2026-08-22, MySQL 26.7 전체 84/84)

### P3~P6 — 기업 MVP

- [ ] P3-01 V5 companies/company_members DDL 및 migration test (2일, P0)
- [ ] P3-02 기업 도메인과 마지막 OWNER invariant 구현 (2일, P0)
- [ ] P3-03 CompanySignupFacade와 기업 가입 API 구현 (1일, P1)
- [ ] P4-01 V6 verification DDL·도메인·관리자 승인/반려 구현 (2일, P1)
- [ ] P4-02 membership authorization 서비스와 권한 매트릭스 테스트 (2일, P0)
- [ ] P4-03 기업 멤버 초대·역할 변경·제거 구현 (1일, P1)
- [ ] P5-01 기업 가입/검증 상태 프론트엔드 구현 (2일, P1)
- [ ] P5-02 개인·기업 가입 E2E 및 접근성/오류 UX 검증 (2일, P1)
- [ ] P6-01 rate limit·audit·metrics 구현 (2일, P0)
- [ ] P6-02 동시성·성능·migration rehearsal·rollback 검증 (2일, P0)

### P7~P8 — Provider 확장

- [ ] P7-01 Provider framework/state registry 정리 (2일, P0)
- [ ] P7-02 Kakao OIDC adapter와 통합 테스트 (2일, P1)
- [ ] P7-03 Naver OAuth adapter와 통합 테스트 (2일, P1)
- [ ] P7-04 Apple adapter·최초 로그인 데이터 처리와 통합 테스트 (3~4일, P1; 분할 구현)
- [ ] P8-01 account link/unlink와 재인증 구현 (2일, P0)
- [ ] P8-02 마지막 identity 보호·충돌·탈취 방지 테스트 (2일, P0)
- [ ] P8-03 전체 Provider UI·운영 보안 점검 (2일, P1)

## 6. 의존성과 차단 조건

- Career Hub V4와 CH-G1이 완료되기 전 기업 V5/V6 구현을 시작하지 않는다.
- V3 배포 전 운영 DB에서 V2와 기존 provider 데이터 분포를 확인한다.
- 기존 Google/GitHub 사용자 backfill 결과가 100% 설명되지 않으면 읽기 전환을 배포하지 않는다.
- Google/GitHub 이메일 자동병합을 제거하기 전 신규 Provider를 추가하지 않는다.
- 기업 권한 매트릭스 테스트가 통과하기 전 기업회원 UI를 운영 활성화하지 않는다.
- Kakao/Naver/Apple 자격증명과 redirect URI 등록은 P7 시작 전 준비한다.
- Apple 외부 설정이 지연되면 Apple만 별도 milestone로 이동한다.

## 7. 검증 게이트

### 데이터 및 마이그레이션

- 운영 실측 `26.7.0-cloud`와 동일 계열인 MySQL 26.7.0: V1→Latest 성공률 100%.
- 운영 V3 익명화 스냅샷: V4→Latest 성공률 100%.
- user identity orphan 및 duplicate 0건.
- 기존 사용자 로그인 가능률 100%.

### 기능 및 보안 평가셋

- LOCAL 50건, Google/GitHub 각 20건 가입·로그인.
- 동일 이메일 소셜 충돌 20건: 자동 연결 0건, `ACCOUNT_LINK_REQUIRED` 100%.
- 동일 이메일/사업자번호/membership 동시 요청 각 20회, concurrency 10, duplicate 0건.
- 기업 역할 4개 × 기업 상태 5개 × 핵심 API 권한 매트릭스.
- Provider별 state 검증, OIDC nonce, 지원 Provider PKCE, redirect allowlist.
- 마지막 identity/OWNER 삭제 차단률 100%.

### KPI와 합격 기준

- DB 구간 signup p95 ≤ 300ms. SMTP/외부 검증/Object Storage는 별도 측정.
- 가입 및 로그인 평가셋 오류율 0%.
- 무권한 기업 API 접근 차단률 100%.
- 약관/기업 검증/역할 변경 감사 이벤트 누락률 0%.
- secret·OAuth token·사업자번호 원문 로그 노출 0건.

## 8. 위험과 완화

| 위험 | 영향 | 완화 |
|---|---|---|
| 현재 이메일 기반 자동병합 | Account Takeover 가능성 | P2에서 최우선 제거, 재인증 전 link 금지 |
| V3 backfill 오류 | 기존 로그인 장애 | 기존 컬럼 유지, dual-read 기간, V2 snapshot rehearsal |
| SMTP가 DB transaction 안에서 실행 | 지연/rollback 불일치 | commit 후 발송, 재발송 가능 상태 유지 |
| 기업 검증 외부 API 의존 | MVP 일정 지연 | 수동 승인 MVP로 분리 |
| Apple 설정·심사 지연 | 전체 Provider 일정 지연 | Apple만 독립 milestone로 이동 |
| 단일 대형 PR | 회귀 범위 확대 | 1 migration 또는 1 capability 단위 PR |

## 9. 세션 재개 프로토콜

새 세션은 다음 순서로 재개한다.

1. `~/.Codex/projects/C--Users-aerof/memory/MEMORY.md`가 존재하면 읽는다.
2. `~/.codex/CONTEXT.md`, `~/.codex/HANDOVER.md`, `~/.codex/AGENTS.md`를 읽는다.
3. 본 문서와 두 Notion source 문서를 읽는다.
4. `git -C C:\Users\aerof\spring\devjobcollector status --short`와 최신 migration을 확인한다.
5. 현재 Phase에서 첫 미완료 체크박스를 선택한다.
6. 해당 작업의 선행 게이트가 통과했는지 검증한다.
7. 구현 후 테스트 결과·커밋 SHA·배포 결과·남은 위험을 HANDOVER와 본 문서에 갱신한다.
8. Notion 통합 실행계획의 현재 Phase, 체크박스, Latest Update도 같은 세션에서 동기화한다.

## 10. 구현 금지사항

- V1/V2/V3 수정 금지.
- `users.account_type` 추가 금지.
- `users.role`에 기업 역할 추가 금지.
- 이메일 기반 외부 identity 식별/자동 연결 금지.
- Provider access token을 DJC API 토큰으로 사용하거나 평문 저장 금지.
- DB native ENUM, 사업자번호 평문, request userId 기반 권한 판단 금지.
- migration test 없이 Flyway 파일 배포 금지.
