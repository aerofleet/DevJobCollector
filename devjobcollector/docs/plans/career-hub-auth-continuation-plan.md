# DJC Career Hub 완성 및 회원·기업 인증 통합 재개 계획

> 상태: In Progress — Career Hub Release 브라우저 QA 대기, CH-P3-06 운영 배포 완료
> 기준일: 2026-08-24 KST
> 현재 브랜치: `main`
> 구현 원칙: Career Hub를 완성한 뒤 회원·기업·Multi-Provider 인증 통합의 첫 미완료 작업으로 복귀한다.

## 1. Source of Truth

- Notion Career Hub 설계: `https://app.notion.com/p/3c69cc8ccb4a817bbce5e0a27c752905`
- Notion 통합 실행계획: `https://app.notion.com/p/3c69cc8ccb4a815b9c8be79ca706cf8b`
- 인증 통합 계획: `docs/plans/member-auth-implementation-plan.md`
- Career Hub 구현 초안: `docs/design/member-career-pages-draft.md`
- G1 운영 기록: `docs/reports/member-auth-production-gate-20260824.md`
- 런타임 핸드오버: `C:/Users/aerof/.codex/HANDOVER.md`

문서가 충돌하면 최신 운영 증거와 본 계획을 우선하되, 인증 보안 계약은 Multi-Provider Authentication v1.1을 유지한다.

## 2. 현재 기준선

### 완료

- 회원 Career Hub UI 3개 로컬 구현
  - `/member`
  - `/my-devjobs`
  - `/resumes`
- 기존 `/resume` 편집기 연결
- 로그인·회원가입 성공 기본 경로 `/member` 전환
- 보호 라우트와 인증 상태별 헤더/로그아웃 구현
- 반응형 스타일 구현
- `npm run lint`: 오류 0건
- `npm run build`: 성공, 1,823 modules transformed
- Cloudflare SPA fallback 설정 확인
- G1 운영 배포와 자동 OAuth HTTPS callback 검증 완료

### 미완료

- Career Hub 브라우저 수동 QA 및 모바일 QA
- 현재 Career Hub 작업 트리 커밋·푸시·배포
- 운영 신규 라우트 3개 HTTP 200 검증
- 실제 GitHub/Google 성공 로그인과 동일 이메일 충돌 수동 검증
- 로그인 사용자 프로필 API
- 저장 공고·지원 현황·최근 본 공고 API/DB
- 이력서 MySQL 영속화·소유권 검증·목록 관리
- G1 수동 OAuth 3건 이후 24시간 관찰
- 기업 인증 통합 P3~P8

## 3. 범위 정의

### Career Hub Release DoD

- 로컬 프론트 구현 검토와 수동 QA 완료
- 변경 커밋·푸시 및 Cloudflare 배포 성공
- 운영 `/member`, `/my-devjobs`, `/resumes` HTTP 200
- 로그인 성공 후 `/member` 도달
- 보호 경로의 로그인 복귀 경로 보존
- 360/768/1024/1440px 치명적 레이아웃 오류 0건

### Career Hub Product DoD

- 회원 프로필을 실제 인증 사용자 기준으로 조회
- 저장 공고·지원 현황·최근 본 공고를 MySQL에 영속화
- 이력서를 MySQL에 영속화하고 회원 소유권 검증
- 이력서 목록/작성/수정/삭제의 핵심 흐름 완료
- Loading/Empty/Error/Success/Unauthorized 상태 분리
- API·E2E·권한 평가셋 통과

Release DoD만 통과한 상태를 Product DoD 완료로 표시하지 않는다.

## 4. 선행 설계 게이트 — Flyway 순서

현재 실제 마이그레이션은 V1~V3만 존재한다. 기존 인증 계획은 기업 테이블을 V4, 검증 테이블을 V5로 예약했지만, 사용자 결정에 따라 Career Hub를 기업 기능보다 먼저 완성해야 한다.

### 권장안

1. Career Hub 회원 활동·이력서 영속화를 V4로 배정한다.
2. 기업 `companies/company_members`를 V5로 이동한다.
3. 기업 검증을 V6로 이동한다.
4. `member-auth-implementation-plan.md`와 연결된 Notion 실행계획을 코드 작성 전에 동기화한다.

### 합격 기준

- 저장소와 Notion의 마이그레이션 번호 충돌 0건
- V1/V2/V3 수정 0건
- MySQL 26.7 clean V1→Latest 및 운영 V3 upgrade 성공률 100%

## 5. 실행 순서

### CH-R1 — 프론트 Release 완료 (1일)

- [x] CH-R1-01 현재 변경 파일과 라우트 리뷰 (2026-08-25)
- [ ] CH-R1-02 360/768/1024/1440px 수동 QA
- [ ] CH-R1-03 키보드 탐색, focus, tab semantics, touch target QA (2026-08-29, 운영 Before touch target 5개 결함 검출; CSS 보정·Frontend `33199530362` 배포 후 운영 키보드/focus 4/4·touch target 10/10 통과, 수동 QA 대기)
- [ ] CH-R1-04 로그인·회원가입·OAuth callback의 `/member` 이동 검증
- [x] CH-R1-05 frontend lint/build 재실행 (2026-08-25: lint 0, build 성공)
- [x] CH-R1-06 기능 커밋 후 push, Actions 배포 확인 (`e11111a`, Actions `32757612547`)
- [x] CH-R1-07 운영 신규 라우트 3/3 HTTP 200과 SPA 새로고침 확인 (2026-08-25)

산출물: 배포된 Career Hub 프론트, QA 결과 문서, 커밋 SHA, Actions run ID.

### CH-P1 — 회원 프로필 및 인증 상태 강화 (1~2일)

- [x] CH-P1-01 현재 JWT claim과 회원 조회 경로 감사 (2026-08-25)
- [x] CH-P1-02 `GET /api/v1/members/me` 계약·구현·테스트 (2026-08-25)
- [x] CH-P1-03 프론트 회원 이름/프로필 연동 (2026-08-25)
- [x] CH-P1-04 API 401 공통 토큰 삭제 및 로그인 복귀 처리 (2026-08-25)
- [x] CH-P1-05 만료·변조·비활성 사용자 평가셋 통과 (2026-08-25, 10/10)

산출물: 실제 회원 프로필, 유효하지 않은 토큰 차단, 401 공통 처리. 커밋 `f1f78fe`, Backend Actions `32829074348`, Frontend Actions `32829074510`, 운영 무토큰 `/members/me` HTTP 401.

### CH-P2 — V4 Career Data Foundation (2일)

- [x] CH-P2-01 Flyway 순서 문서 동기화 (2026-08-25: Career V4, Company Core V5, Verification V6)
- [x] CH-P2-02 V4 DDL 작성 (2026-08-25, `3fef55b`)
  - `resumes`
  - `job_bookmarks`
  - `job_view_history`
  - `applications`
- [x] CH-P2-03 Entity/Repository 및 회원 FK·unique·index 구현 (2026-08-25, `c8b48fd`, Actions `32849241829`·`32852535004`·`32852535010`)
- [x] CH-P2-04 clean/V3 upgrade migration test (2026-08-25, MySQL 26.7 Actions `32841106449`, `32842721118`)
- [x] CH-P2-05 ownership/orphan/duplicate read-only audit 작성 (2026-08-26, `66b9fe2`, Actions `32864748869`·`32866285746`·`32866285666`)

산출물: 회원 소유 Career 데이터 기반과 MySQL 26.7 평가 결과.

### CH-P3 — 마이데브잡 기능 완성 (3~4일)

- [x] CH-P3-01 북마크 생성·조회·삭제 API (2026-08-26, `ff5590f`, Actions `32879495958`·`32880026361`·`32880026369`)
- [x] CH-P3-02 최근 본 공고 기록·조회·보존 한도 정책 (2026-08-26, 회원당 100개, `f5f1a50`, Actions `32948243993`·`32948243996`)
- [x] CH-P3-03 지원 생성·조회·상태 변경 API (2026-08-26, `6444274`, Actions `32957389993`·`32957390045`)
- [x] CH-P3-04 `/my-devjobs` 실제 데이터 연동 (2026-08-26, `1e4d59f`, Frontend Actions `32971481254`)
- [x] CH-P3-05 Loading/Empty/Error/Unauthorized 상태 구현 (2026-08-26, 공통 401 복귀 경로 유지)
- [x] CH-P3-06 동시 요청·타 회원 데이터 접근 차단 테스트 (2026-08-27: 20회/concurrency 10, 소유권 5종, MySQL 전체 게이트·운영 배포 통과)

산출물: 저장 공고·지원 현황·최근 본 공고의 회원별 실제 동작.

### CH-P4 — 이력서 플랫폼 완성 (4~5일)

- [x] CH-P4-01 현재 메모리 `ResumeService` 제거 계획과 API 계약 확정 (2026-08-27, `docs/design/resume-platform-api-contract.md`)
- [x] CH-P4-02 회원별 이력서 목록/생성/조회/수정/상태 변경/삭제 구현 (2026-08-27, 신규 평가셋 8/8·전체 Gradle 성공)
- [x] CH-P4-03 `/resumes` 목록·상태·수정 시각 연동 (2026-08-27, Loading/Empty/Error/Success/Unauthorized)
- [x] CH-P4-04 `/resume` 신규/수정 모드와 저장 후 복귀 흐름 구현 (2026-08-27, lint 오류 0·build 1,828 modules)
- [x] CH-P4-05 다른 회원 이력서 IDOR 차단 (2026-08-27, 조회·수정·상태·삭제 4/4 HTTP 404 계약)
- [x] CH-P4-06 재시작 후 데이터 유지 및 rollback rehearsal (2026-08-27, MySQL 26.7 Resume 2/2·전체 게이트 54/54·이전 HEAD 6/6)

산출물: MySQL 기반 회원 소유 이력서 관리와 편집 흐름.

### CH-G1 — 통합·운영 게이트 (2일 + 24시간 관찰)

- [x] CH-G1-01 백엔드 전체 Gradle 및 MySQL 26.7 평가셋 (2026-08-28, clean test 성공·MySQL 54/54 skip 0)
- [x] CH-G1-02 프론트 lint/build/E2E (2026-08-28, lint 0·build 1,828 modules·Playwright 8/8, 4 viewport overflow 0px)
- [x] CH-G1-03 운영 배포와 health/search 회귀 (2026-08-28, Backend `33105941627`·Docker `33105941628`·Frontend `33105941650`·E2E 추가 배포 `33108243400`, 운영 smoke 및 보호 경로 4×4 viewport E2E 16/16 합격)
- [x] CH-G1-04 Career Hub 핵심 사용자 여정 E2E (2026-08-31, 점 세 개 작업 메뉴 기준 운영 Resume 생성·수정·재조회·삭제 1/1, skip 0, 테스트 데이터 잔존 0건; Actions `33319783743` 성공)
- [x] CH-G1-05 Google 로그인 성공 (2026-09-03, 검증 이메일 claim 호환 배포 후 실제 로그인·기본 페이지 이동 사용자 확인)
- [x] CH-G1-06 GitHub 로그인 성공 (2026-09-02, 실제 승인·JWT 발급·`GET /members/me` 200·ACTIVE/USER)
- [x] CH-G1-07 동일 이메일 `ACCOUNT_LINK_REQUIRED`, 자동 연결 0건 (2026-09-03, 충돌 재현 후 기존 계정 재인증·명시적 연결 경로 확인)
- [ ] CH-G1-08 24시간 인증·Career API 오류율 관찰

종료 게이트: G1 합격 전 기업 기능이나 신규 OAuth Provider 운영 활성화를 시작하지 않는다.

#### CH-G1-05·07 실제 OAuth 및 계정 연결 완료 — 2026-09-03

- 실제 Google 단독 로그인과 기본 페이지 이동을 사용자 브라우저에서 확인했다.
- 동일 이메일의 미등록 Google identity는 최초 시도에서 `ACCOUNT_LINK_REQUIRED`로 종료되어 자동 병합되지 않았다.
- 기존 GitHub 계정 재인증 후에만 Google 연결을 재개했으며, 연결 충돌 callback은 유효한 로그인 상태에서 기본 페이지로 복귀하도록 정규화했다.
- Google 검증 이메일 claim은 `email_verified`와 `verified_email`을 모두 지원하되 값이 `true`일 때만 신뢰한다.
- 계정 연결은 최근 5분 이내 기존 인증, 5분 만료 서버 세션 intent, 동일한 검증 이메일, 미소유 provider subject를 모두 요구한다.

목표 KPI는 Google 실제 로그인 성공률 100%(1/1), 동일 이메일 자동 연결 0건, 명시적 계정 연결 성공률 100%(1/1), callback 오류 화면 잔존 0건이다. 평가셋은 사용자 브라우저 Google Journey 1건, 동일 이메일 충돌·재인증·연결 Journey 1건, 백엔드 전체 Gradle 회귀, 프론트 계정 연결 Playwright 시나리오다. Before에는 Google callback이 `ACCOUNT_LINK_REQUIRED` 또는 `ACCOUNT_LINK_CONFLICT` 화면에 머물렀다. After에는 자동 병합 없이 기존 계정 확인을 거친 연결 또는 독립 로그인이 완료되고 기본 페이지로 이동한다. 합격 기준은 위 사용자 Journey 2/2, 자동 병합 0건, 전체 회귀 실패 0건이다.

#### CH-G1-08 관찰 자동화 준비 — 2026-09-03

- 인증·Career 경로의 완료 상태를 Spring Security 필터 앞에서 집계용 로그로 기록한다.
- 기록 필드는 `scope`, HTTP method, URI path, status뿐이며 query string, 헤더, Cookie, 토큰, 이메일은 기록하지 않는다.
- 수동 Actions는 운영 원문 로그를 출력·업로드하지 않고 인증·Career 요청 수, 5xx 수·비율, OAuth 처리 실패 수만 Job Summary에 남긴다.
- 읽기 전용 synthetic 평가셋은 health·공개 검색 200과 무토큰 회원·Career API 401 총 7건이다.
- 목표 KPI: 24시간 인증·Career API 5xx 비율 < 1%, OAuth 처리 실패 0건, 원문 로그 외부 노출 0건.
- 합격 기준: 최신 관찰 필터 배포 이후 연속 24시간 집계에서 위 KPI 충족. 배포 전 또는 24시간 미만 결과는 baseline으로만 사용한다.

#### CH-G1-04 진행 기록 — 2026-08-30

- 목표 KPI: 운영 인증 Resume 생성·수정·재조회·삭제 성공률 100%(1/1), 테스트 데이터 잔존 0건
- 평가셋: `desktop-1440`, `DJC_E2E_ACCESS_TOKEN`을 사용하는 `npm run e2e:production:authenticated` 1건
- Before/After: 직접 노출된 `수정하기` 링크 선택자 → 카드 점 세 개 메뉴의 `수정` menuitem 선택자로 갱신
- 현재 결과: ESLint 오류 0건, production build 1,828 modules, 로컬 Resume Journey 4 viewport 12/12, 운영 인증 평가는 토큰 미설정으로 1건 skip
- 합격 기준: 운영 POST 201·PUT 200·재조회 일치·DELETE 200/204·삭제 후 0건을 모두 충족하고 skip 0건
- 차단 조건: 현재 셸에 `DJC_E2E_ACCESS_TOKEN`이 없고 인앱 브라우저 세션을 초기화할 수 없어 실제 운영 쓰기 평가는 대기한다. 토큰 원문은 문서·Git·로그에 남기지 않는다.

#### CH-G1-04 재현 가능한 운영 게이트 — 2026-08-31

- 목표 KPI: 운영 인증 Resume Journey 1/1 성공, skip 0건, 테스트 데이터 잔존 0건
- Before/After: 로컬 셸 토큰에만 의존 → GitHub Secret 기반 `DJC Career Hub Authenticated E2E` 수동 워크플로로 반복 실행 가능
- 평가셋: `desktop-1440`에서 생성 POST 201, 수정 PUT 200, 재조회 제목 일치, 삭제 200/204, 삭제 후 카드 0건
- 보안 기준: Secret 원문 로그 출력 0건, Playwright trace 비활성화, 테스트 데이터 실패 시 정리 재시도
- 현재 결과: GitHub Secret 등록 후 Actions `33319783743` 성공, Playwright 1 passed / 0 skipped, 생성 테스트 데이터 정리 확인
- 해제 절차: `gh secret set DJC_E2E_ACCESS_TOKEN`으로 값을 안전하게 입력한 후 `gh workflow run djc-career-hub-authenticated-e2e.yml --ref main` 실행
- 합격 기준: Actions conclusion `success`, Playwright 1 passed / 0 skipped, 생성한 `CH-G1-04` 이력서 잔존 0건

#### CH-G1-04 완료 기록 — 2026-08-31

- 평가 실행: `DJC Career Hub Authenticated E2E` Actions `33319783743`
- 결과: `desktop-1440` 1 passed / 0 skipped, 실행 시간 7.5초
- API/UI 검증: POST 201, PUT 200, 재조회 제목 일치, DELETE 200/204, 삭제 후 카드 0건
- 보안 결과: Actions 로그의 access token 원문 노출 0건(마스킹 확인), Playwright trace 비활성화
- 합격 판정: 목표 KPI 1/1 성공과 테스트 데이터 잔존 0건을 충족하여 CH-G1-04 완료
- 다음 재개점: CH-G1-08 배포 후 24시간 인증·Career API 오류율 관찰

#### CH-G1-06 GitHub callback Whitelabel 방어 — 2026-09-01

- [x] OAuth user-info/upsert와 success handler의 예상 밖 런타임 예외 경계 분리
- [x] 예상 밖 예외를 Frontend `OAUTH_LOGIN_FAILED` callback으로 전환해 Whitelabel `/error` 노출 방어
- [x] Provider·예외 타입·root cause 타입만 남기는 민감정보 비포함 진단 로그 적용
- [x] 회귀 테스트 3/3, 전체 Gradle `BUILD SUCCESSFUL`
- [x] Backend Actions `33422711315`, Docker Actions `33422711318` 성공
- [x] 배포 후 비파괴 운영 smoke HTTP 5/5, OAuth HTTPS callback 2/2 통과
- [x] 실제 GitHub 계정 승인 및 `/oauth/callback` 성공 1/1
- [x] GitHub 로그인 토큰으로 `GET /api/v1/members/me` HTTP 200 1/1 — ACTIVE/USER
- [ ] `/member` 화면 도달 1/1 — CH-R1 OAuth UI 이동 게이트로 유지

목표 KPI는 Whitelabel `/error` 노출 0건, callback 오류의 Frontend 안내 전환율 100%, 민감정보 로그 노출 0건, 실제 GitHub 로그인 성공 1/1이다. 이 작업은 로그인 후 Career Hub 핵심 기능 도달률 100%와 운영 인증 오류율 1% 미만 KR에 연결된다.

평가셋은 단위 회귀 3건, 전체 Gradle 1회, Backend·Docker CI 각 1회, 운영 HTTP smoke 5건과 OAuth callback 2건, 실제 GitHub 계정 Journey 1건이다. Before에는 예상 밖 callback 예외가 `/error` 404로 노출되고 OAuth 처리 단계별 안전 로그가 없었다. After에는 예상 밖 예외가 Frontend 오류 callback으로 복귀하고 안전한 예외 타입 로그를 남기며, 자동 평가셋은 모두 합격했다. 최종 합격은 실제 GitHub 승인 후 `/member` 도달과 `/members/me` 200이 각각 1/1이고 callback 5xx·Whitelabel·민감정보 노출이 모두 0건일 때로 제한한다.

#### CH-G1-06 callback 500 재현 및 2차 방어 — 2026-09-01

- [x] 첫 방어 배포 후 실제 GitHub callback Whitelabel HTTP 500 재현 — `2026-09-01T05:22:44Z`
- [x] `/login/oauth2/code/*` 최상위 필터로 handler 바깥 RuntimeException·ServletException 정규화
- [x] failure handler 자체 실패 시 고정 401 JSON fallback 적용
- [x] 신규 필터 단위 평가 3/3 및 전체 Gradle `BUILD SUCCESSFUL`
- [x] 커밋 `eaa7438`, Backend Actions `33492263211`, Docker Actions `33492263233` 성공
- [x] 운영 비파괴 smoke HTTP 5/5, OAuth HTTPS callback 2/2 통과
- [x] 새 배포 기준 실제 GitHub 로그인 및 `/members/me` 200
- [ ] `/member` 화면 도달 확인
- [ ] 실패 시 안전 로그의 예외 타입으로 근본 원인 확정

#### CH-G1-06 실제 로그인 성공 및 JWT 노출 후속 — 2026-09-02

- [x] 실제 GitHub 승인·JWT 발급
- [x] `GET /api/v1/members/me` HTTP 200, JSON, ACTIVE/USER
- [x] CORS 허용 Origin과 Frontend Origin 일치
- [x] 개발 중 `AUTH_TOKEN_SECRET` 교체 유예와 현재 개발 토큰 사용 위험 수용 결정 — 2026-09-02
- [x] 토큰 원문 Git·Notion·로그·추가 대화 기록 금지
- [ ] 개발 종료 또는 운영 전 Secret 교체 여부 재평가
- [ ] 교체 시 기존 JWT 보호 API 401 및 전체 세션 로그아웃 영향 확인
- [ ] `/member` 화면 도달 확인

목표 KPI는 GitHub 실제 로그인과 인증 API 성공률 각 100%(1/1), ACTIVE 판정 100%, Git·Notion·애플리케이션 로그의 JWT 원문 추가 노출 0건, 운영 전 보안 결정 재검토 1/1이다. 실제 GitHub 승인 1건과 JWT 인증 `GET /members/me` 1건에서 200·ACTIVE/USER를 확인했다. Before는 callback Whitelabel 500과 인증 API 성공 미측정, After는 JWT 발급 및 인증 프로필 200 1/1이다. 개발 편의를 위해 Secret 교체는 유예하지만 이 결정은 개발 단계에만 적용하며, 운영 전 Secret 교체 또는 유지 근거 승인을 합격 기준으로 둔다.

목표 KPI는 callback Whitelabel·5xx 각 0건, 오류 응답 정규화율 100%, 민감정보 로그 노출 0건, 실제 GitHub 로그인 1/1이다. 평가셋은 필터 단위 3건, 전체 Gradle 1회, CI 2건, 운영 smoke HTTP 5건·OAuth callback 2건, 실제 GitHub Journey 1건이다. Before에는 handler 바깥 예외가 `/error` HTTP 500으로 노출됐다. After에는 해당 예외도 Frontend 오류 callback으로 전환하고 handler 실패 시 401 JSON으로 종결한다. 실제 GitHub 승인 후 `/member`와 `/members/me` 200이 각각 1/1이고 Whitelabel·callback 5xx·민감정보 노출이 0건일 때만 완료한다.

### AUTH-P3~P8 — 회원·기업 인증 통합 재개

Career Hub Product DoD와 CH-G1 완료 후 `member-auth-implementation-plan.md`의 첫 미완료 작업으로 복귀한다.

1. P3-01: 기업 Core 마이그레이션(권장 V5)과 migration test
2. P3-02: Company/CompanyMember와 마지막 OWNER invariant
3. P3-03: CompanySignupFacade 및 기업 가입 API
4. P4: 기업 검증(권장 V6), 권한 매트릭스, 멤버 초대·역할 관리
5. P5: 기업 가입·검증 상태 UI와 개인/기업 E2E
6. P6: rate limit, audit, metrics, concurrency, rollback
7. P7: Kakao/Naver/Apple Provider 확장
8. P8: account link/unlink와 최종 보안 검증

## 6. KPI / OKR / 평가셋

### 목표 KPI

- 로그인 후 `/member` 도달률 100%
- Career Hub 핵심 기능 도달 클릭 수 1회 이하
- 보호 경로 비인증 차단률 100%
- 타 회원 Career 데이터 접근 차단률 100%
- 마이그레이션 성공률 100%
- 평가셋 API 오류율 0%
- 360px 가로 스크롤 0px
- ESLint 오류 0건, production build 성공률 100%
- 운영 24시간 인증/Career API 5xx 비율 < 1%

### OKR 연결

- Objective: 공개 채용 탐색을 회원의 지속적인 커리어 관리 여정으로 연결한다.
- KR1: 로그인 후 회원 기능 도달률 100%.
- KR2: 북마크·지원·최근 조회·이력서 데이터 유실 0건.
- KR3: 회원 및 기업 권한 평가셋의 무권한 접근 허용 0건.

### 평가셋

- 화면: 4 route × 4 viewport = 16건
- 인증: 이메일·Google·GitHub·만료 토큰·비활성 계정·충돌 = 최소 6시나리오
- 권한: 본인/타인/비인증의 Career 데이터 CRUD
- 동시성: 북마크·지원·이력서 중복 요청 각 20회, concurrency 10
- DB: clean V1→Latest와 운영 V3→Latest
- 운영: health, 공개 검색, 회원 프로필, Career 핵심 API, 신규 route

## 7. 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| V4/V5 번호 충돌 | 배포 순서와 문서 불일치 | CH-P2 시작 전 저장소·Notion 동기화 |
| 메모리 이력서 저장 | 재시작 시 데이터 유실 | MySQL 전환 전 운영 완료 처리 금지 |
| 토큰 존재 여부 인증 | 만료 토큰 오판 | `/me`와 401 전역 처리 |
| 빈 상태 UI만 존재 | 실제 사용자 가치 부족 | Product DoD에 API 연동 포함 |
| 실제 OAuth 수동 QA 지연 | G1 보류 | 자동 callback 검증 유지, 운영자 승인 3건 기록 |
| Career 범위 확대로 기업 일정 지연 | 기업 MVP 지연 | 1~2일 WP, 단계별 커밋, CH-G1 이후 즉시 P3 복귀 |

## 8. 커밋 경계

1. `feat: member career hub pages`
2. `feat: member profile api integration`
3. `feat: career data foundation`
4. `feat: my devjobs activity management`
5. `feat: persistent resume management`
6. `docs: career hub production gate`

각 커밋은 독립 검증 결과와 rollback 가능 범위를 가진다. Co-Authored-By는 추가하지 않는다.

## 9. 세션 재개 프로토콜

새 세션은 다음 순서로 진행한다.

1. `C:/Users/aerof/.codex/CONTEXT.md`
2. `C:/Users/aerof/.codex/HANDOVER.md`
3. 본 계획
4. Notion Career Hub 원문
5. `docs/plans/member-auth-implementation-plan.md`
6. `git -C C:/Users/aerof/spring status -sb`
7. 본 계획의 첫 미완료 체크박스와 선행 게이트 확인
8. 구현 후 테스트·커밋·배포·잔여 위험을 본 계획과 HANDOVER에 갱신

현재 재개 지점은 **CH-G1-08 24시간 인증·Career API 오류율 관찰**이다. Google·GitHub 실제 로그인과 동일 이메일 자동 병합 차단·명시적 연결을 완료했으며, 관찰 필터 배포 시각부터 연속 24시간을 집계한다. 관찰 합격 전 기업 기능으로 이동하지 않고 운영 전 인증 보안 재검토를 별도 게이트로 유지한다. 현재 사용자 변경은 삭제·reset·restore하지 않는다.
