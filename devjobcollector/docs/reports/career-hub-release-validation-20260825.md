# DJC Career Hub 프론트 Release 검증

## 상태

- 기준일: 2026-08-25 KST
- 대상: `/member`, `/my-devjobs`, `/resumes`, 기존 `/resume`
- 현재 판정: 정적 게이트 합격, 브라우저 수동 QA 및 운영 배포 대기

## 변경 범위

- 로그인·회원가입 성공 기본 경로를 `/member`로 변경
- 회원 전용 랜딩, 마이데브잡, 이력서 관리 라우트 추가
- 세 라우트에 `ProtectedRoute` 적용
- 로그인 상태별 헤더·모바일 메뉴·로그아웃 구현
- 데스크톱 sidebar, 태블릿 top tabs, 모바일 single-column 레이아웃 구현
- `/resumes` 관리 화면에서 기존 `/resume` 편집기로 연결

## KPI / OKR

### 목표 KPI

- ESLint 오류: 0건
- production build 성공률: 100%
- 신규 보호 route 코드 등록률: 3/3
- 로그인 성공 기본 경로 `/member` 적용률: 2/2
- 비로그인 보호 경로 차단률: 100%
- 360px horizontal scroll: 0px
- 운영 신규 route HTTP 200: 3/3

### OKR 연결

- Objective: 로그인 이후 공개 메인으로 단절되던 흐름을 회원 Career Hub로 연결한다.
- KR1: 로그인 후 회원 핵심 기능까지 1-click 이내 도달한다.
- KR2: 데스크톱·태블릿·모바일 치명적 레이아웃 오류를 0건으로 유지한다.

## 평가셋

### 정적 평가

| 항목 | 명령/대상 | 결과 |
|---|---|---|
| Diff 형식 | `git diff --check` | 성공 |
| ESLint | `npm run lint` | 성공, 오류 0건 |
| Production build | `npm run build` | 성공 |
| Build modules | Vite 7.3.1 | 1,823 modules transformed |
| SPA fallback | `wrangler.jsonc` | `single-page-application` 확인 |
| 보호 route | `/member`, `/my-devjobs`, `/resumes` | 코드 등록 3/3 |
| 기본 이동 | 로그인, 이메일 가입 인증 완료 | `/member` 적용 2/2 |

### 브라우저 평가

| 화면 | 360 | 768 | 1024 | 1440 |
|---|---:|---:|---:|---:|
| `/member` | 대기 | 대기 | 대기 | 대기 |
| `/my-devjobs` | 대기 | 대기 | 대기 | 대기 |
| `/resumes` | 대기 | 대기 | 대기 | 대기 |
| `/resume` | 대기 | 대기 | 대기 | 대기 |

인앱 브라우저 연결 환경 오류로 시각·키보드 QA를 수행하지 못했다. 별도 자동화 브라우저로 우회하지 않았으며 완료로 표시하지 않는다.

## Before / After

| 항목 | Before | After |
|---|---|---|
| 로그인 기본 이동 | `/` | `/member` |
| 회원 랜딩 | 없음 | `/member` |
| 채용 활동 관리 | 없음 | `/my-devjobs` Empty State UI |
| 이력서 관리 허브 | 없음 | `/resumes` |
| 이력서 편집 | `/resume` 단독 | `/resumes` → `/resume` |
| 인증 헤더 | 로그인·회원가입 고정 | 마이데브잡·로그아웃 전환 |

## 합격 기준과 현재 판정

- [x] `git diff --check` 성공
- [x] `npm run lint` 오류 0건
- [x] `npm run build` 성공
- [x] 신규 보호 route 3개 등록
- [x] 로그인·회원가입 기본 이동 `/member`
- [ ] 4 route × 4 viewport 수동 QA 16/16
- [ ] 키보드 탐색·focus·tab semantics 확인
- [ ] 기능 커밋·push·Cloudflare 배포 성공
- [ ] 운영 신규 route HTTP 200 3/3
- [ ] 실제 로그인 후 `/member` 도달 확인

Release DoD는 미완료다. 남은 항목을 검증한 뒤 완료 처리한다.
