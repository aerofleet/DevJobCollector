# DJC 관리자 시스템 P0 현행 실사 보고서

## 목적

Notion 관리자 명세의 가정을 2026-09-24 `main` 코드와 대조해 구현 입력값을 확정한다. 운영 비밀값·실 IP·OCID는 기록하지 않는다.

## 확인 결과

| 영역 | 실제 상태 | 관리자 구현 영향 |
|---|---|---|
| 회원 | `users.status`: `PENDING_EMAIL`, `ACTIVE`, `SUSPENDED`, `WITHDRAWN` | MVP 전이는 `ACTIVE ↔ SUSPENDED`; `WITHDRAWN` 제외 |
| 회원 인증 | 일반 회원 JWT와 OAuth, 프론트 `localStorage` 토큰 | 관리자 인증과 principal을 완전 분리 |
| 커리어 데이터 | V4 `resumes`, `job_bookmarks`, `job_view_history`, `applications` 영속화 완료 | 명세의 “원천 데이터 미완” 가정은 해소됨; 관리자 조회는 접근통제 이후 가능 |
| 이력서 | MySQL JSON 영속화 및 회원 소유권 기반 API 존재 | `RESUME_READ`와 열람 사유/단기 허가를 추가해야 함 |
| 기업 | V5 `companies`, `company_members`; V6 검증 요청 | 신규 테이블 재설계보다 현재 모델 확장 우선 |
| 기업 상태 | `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `SUSPENDED`, `CLOSED` | 명세 용어를 실제 enum에 매핑 |
| 기업 심사 | 일반 회원 `PLATFORM_ADMIN` 역할을 이용한 `/api/v1/admin/company-verification-requests/**` 존재 | 관리자 전용 세션으로 교체하고 기존 호환 경로 제거 계획 필요 |
| 공고 | `job_posts.is_active` boolean, 수집 공고 중심 | HIDDEN/CLOSED 분리를 위한 migration과 수집기 overwrite 방지 필요 |
| 보안 감사 | V7 `security_audit_events`는 기업 보안 이벤트 전용 | 범용 append-only `admin_audit_logs` 별도 필요 |
| Backend 배포 | 루트 `.github/workflows/djc-backend-deploy.yml`, GHCR/Docker Compose | 하위 `devjobcollector/.github`의 구형 nohup workflow는 운영 소스가 아님 |
| Frontend 배포 | 루트 `.github/workflows/djc-frontend-deploy.yml`, Workers | 관리자용 독립 workflow와 Worker 이름 필요 |

## 스키마 기준

- Flyway 운영 이력은 V1~V7이다. 관리자 신규 migration은 V8부터 추가한다.
- `users`에는 JPA 낙관적 잠금용 version 컬럼이 없다. 관리자 상태 변경 경쟁 제어를 위해 단계적 추가가 필요하다.
- `companies`에도 version 컬럼이 없다. 검증 요청에는 PENDING 행 비관적 잠금 로직이 있으나 관리자 API 계약의 `expectedVersion`과 일치시키려면 version 전략이 필요하다.
- `job_posts`는 외부 수집 데이터 갱신 시 `is_active=true`로 복구될 수 있어 관리자 숨김 상태를 같은 필드로 표현하면 안 된다.

## 보안 격차

1. 현재 `/api/v1/admin/**`은 일반 회원 JWT 인증만 요구하고, 일부 서비스에서 `users.role=PLATFORM_ADMIN`을 검사한다.
2. 전역 CSRF가 비활성화되어 있다. 관리자 쿠키 인증 도입 시 별도 SecurityFilterChain과 CSRF/Origin 검사가 필수다.
3. 관리자 계정·세션·MFA·잠금·세션 폐기 저장소가 없다.
4. 범용 관리자 감사 로그와 request ID 연결이 없다.
5. 관리자 응답의 `Cache-Control: no-store` 정책이 없다.

## 배포 격차

- 관리자 SPA는 `admin-frontend`로 분리하고 `devjobs-admin` Worker로 배포한다.
- API origin은 환경 변수로 주입하되 브라우저 공개값이라는 전제를 유지한다.
- custom domain과 Access/Tunnel은 코드 외 Cloudflare/OCI 설정 확인 후 활성화한다.
- preview origin을 운영 CORS allowlist에 자동 추가하지 않는다.

## 평가 기준

- **목표 KPI**: 실사 누락으로 인한 migration/API 재작업 0건.
- **평가셋**: Flyway 7개, 주요 Entity 5종, SecurityFilterChain 1개, 실제 루트 배포 workflow 5개를 대조한다.
- **Before**: 명세에 커리어 데이터·이력서·기업 코드가 미확인 또는 미완으로 기록됨.
- **After**: 구현 완료 영역과 신규 개발 영역을 표로 분리하고 V8 시작점을 확정함.
- **합격 기준**: 상태 enum, 테이블, 인증 경계, 배포 경로가 코드 근거와 100% 일치.

## 결론

관리자 프로젝트는 신규 독립 서비스가 아니라 기존 Spring Boot 안의 관리자 보안 경계와 독립 BO SPA로 진행한다. P1의 첫 변경은 V8 관리자 기반 스키마와 관리자 전용 SecurityFilterChain이며, P3 프론트 기반은 API 구현과 병행 가능하다.
