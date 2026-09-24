# DJC 관리자 시스템 구현 계획

- 기준 명세: [DJC 관리자 시스템 아키텍처 및 개발 명세서 v1.0](https://app.notion.com/p/3e59cc8ccb4a815bb008ee988092a46d)
- 기준일: 2026-09-24
- 배포 결정: 관리자 React SPA는 Cloudflare Workers Static Assets, 관리자 API는 기존 Spring Boot/OCI
- 단계 0 근거: `docs/reports/admin-phase0-current-state-audit-20260924.md`

## 목표와 성공 기준

- **OKR 연결**: 운영자가 회원·기업·공고 조치를 단일 BO에서 추적 가능하게 하여 수동 DB 조작을 0건으로 만든다.
- **보안 KPI**: 일반 회원 토큰 관리자 API 접근 0/전체 허용, 만료·변조 세션 100% 401, 권한 위반 100% 403.
- **업무 KPI**: 회원 상태 변경·기업 심사·공고 숨김의 성공 건은 감사 로그와 100% 원자적으로 기록한다.
- **성능 KPI**: 목록 API p95 500ms 이하, 대시보드 요약 API p95 800ms 이하, BO 초기 해시 자산 캐시 적중 후 LCP p75 2.5초 이하.
- **가용 KPI**: 승인된 BO 배포의 `/login` 및 직접 경로 새로고침 성공률 100%.
- **평가셋**: 역할별 API 40건 이상, 상태 전이/동시성 20회(concurrency 10), 360/768/1024/1440px E2E, MySQL 26.7 migration/rollback rehearsal.
- **합격 기준**: 필수 보안 시나리오 전부 통과, 회귀 실패 0건, 민감 필드 로그 노출 0건, 배포 smoke 5/5.

## 확정된 기술 방향

1. `admin-frontend`를 회원용 `frontend`와 분리한다. 관리자 앱은 일반 회원 `localStorage` 토큰을 읽지 않는다.
2. 관리자 인증은 HttpOnly/Secure/SameSite 쿠키와 CSRF 토큰을 사용한다. 프론트 요청은 `credentials: include`를 고정한다.
3. `/api/v1/admin`은 관리자 전용 인증 필터·principal을 사용한다. 현재 일반 회원 JWT 기반 관리자 경로를 단계적으로 대체한다.
4. 업무 변경과 `admin_audit_logs` 기록은 같은 MySQL 트랜잭션에서 처리한다.
5. 상태 변경은 `expectedVersion`과 조건부 갱신으로 충돌을 409로 반환한다.
6. BO는 Cloudflare Workers Static Assets로 배포하며 비밀값을 `VITE_*`에 넣지 않는다.

## 단계별 작업

### P0 — 실사와 계약 고정

- [x] 실제 Flyway V1~V7 및 Entity 상태값 확인
- [x] 회원 활동·이력서·기업 기능 구현 여부 확인
- [x] 실제 GitHub Actions 및 Docker/GHCR 운영 경로 확인
- [x] 관리자 프론트/API 경계와 배포 방식 확정
- [x] 초기 SUPER_ADMIN 1명 및 MFA 방식 TOTP 확정(WebAuthn은 후속 검토)
- [ ] 관리자 API 공개 도메인과 Cloudflare Access/Tunnel 정책 확인

### P1 — 관리자 인증·감사 기반

- [x] V8 관리자 계정·세션·감사·상태 이력 스키마 및 롤백 절차
- [x] 초기 SUPER_ADMIN 일회성 프로비저닝
- [x] 로그인 실패 5회·30분 잠금, TOTP, 폐기 가능한 8시간 서버 세션
- [x] 관리자 인증 경로 전용 SecurityFilterChain, CSRF/Origin, request ID, no-store
- [x] `/auth/login`, `/auth/logout`, `/me`
- [x] 일반 회원 JWT와 관리자 세션 격리 평가셋

### P2 — 운영 MVP API

- [ ] 대시보드 요약·추이
- [ ] 회원 목록·상세·정지/해제 및 세션 폐기
- [ ] 기업 목록·상세·승인/반려
- [ ] 공고 목록·상세·숨김/복구·강제 마감
- [ ] 감사 로그 검색
- [ ] 관리자 계정 관리(SUPER_ADMIN)
- [ ] idempotency key, optimistic conflict, 감사 원자성 평가셋

### P3 — 관리자 SPA

- [x] 독립 Vite/React 프로젝트와 Workers Static Assets 설정
- [x] 쿠키 기반 API client, 인증 guard, 반응형 셸
- [x] 대시보드 로딩·오류·N/A 상태
- [ ] 회원·기업·공고 서버 페이지네이션 목록/상세
- [ ] 상태 변경 확인 패널과 사유/버전/중복 제출 방지
- [ ] 감사·관리자 화면 역할 가드
- [ ] 접근성 및 4개 viewport E2E

### P4 — 운영 검증과 배포

- [ ] MySQL 26.7 clean/upgrade/rollback rehearsal
- [ ] Cloudflare preview에서 쿠키·CORS·CSRF 검증
- [ ] `admin.itsdev.kr` custom domain 및 선택적 Cloudflare Access
- [ ] 원본 8080/DB 3306 직접 접근 차단 확인
- [ ] 역할별 실계정 QA, 배포 smoke, 롤백 리허설

## 의존성과 미확정 사항

- 관리자 API origin이 `api.itsdev.kr`인지 기존 DJC API origin인지 운영 확인이 필요하다. 프론트는 `VITE_ADMIN_API_BASE_URL`로 분리한다.
- MFA 방식과 초기 관리자 전달 절차는 보안 정책 결정이 필요하다. 구현 기본안은 TOTP다.
- 현재 `job_posts`는 `is_active`만 있어 HIDDEN과 CLOSED를 구분할 수 없다. P2 전에 상태 컬럼과 수집 갱신 정책을 확정한다.
- 현재 `companies.status`는 `PENDING_VERIFICATION/VERIFIED/REJECTED/...`이며 명세의 `PENDING/APPROVED`를 그대로 사용하지 않는다.
- 감사 로그 보존 기간과 증빙 원본 보관 기간은 개인정보 정책 확인이 필요하다.

## 진행 현황

- P0: 코드 실사 완료, 운영 정책 2건 확인 대기
- P1: 관리자 인증·감사 기반 6/6 완료; 전체 migration/rollback 리허설은 P4 합격 게이트로 유지
- P2: 미착수
- P3: 기반 작업 진행 중
- P4: 미착수
