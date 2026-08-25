# Career Hub 회원 프로필·인증 검증 결과

> 검증일: 2026-08-25 KST  
> 범위: CH-P1-01~05  
> 운영 반영: 미배포 — 로컬 구현·회귀 검증 완료

## 결과

- `GET /api/v1/members/me`를 추가하고 JWT subject의 회원을 DB에서 다시 조회한다.
- `users.status=ACTIVE`이고 `personal_profiles.profile_status!=DELETED`인 회원만 응답한다.
- 응답 계약은 `id`, `email`, `name`, `role`, `profileStatus`다.
- Career Hub 사이드바의 하드코딩 이름을 실제 회원 이름과 첫 글자 아바타로 교체했다.
- 회원·이력서 API가 공통 Axios 클라이언트를 사용하며, 401이면 토큰 삭제 후 현재 경로를 보존해 로그인으로 이동한다.
- `/api/v1/members/**`와 `/api/v1/resume/**`는 무인증 요청에 401을 반환한다.

## KPI / OKR

- 목표 KPI
  - 보호 API 무인증·무효 인증 차단률 100%
  - 정상 회원 프로필 응답 계약 통과율 100%
  - 프런트 ESLint 오류 0건
  - production build 성공률 100%
- OKR 연결
  - Objective: 공개 채용 탐색을 로그인 회원의 지속적인 Career Hub 여정으로 연결한다.
  - KR: 로그인 회원 기능 도달률 100%, 무권한 회원 데이터 접근 허용 0건.

## 평가셋과 결과

| 구분 | 평가 조건 | 기대 결과 | 결과 |
|---|---|---|---|
| JWT | 정상 서명·미만료 | subject 검증 성공 | 통과 |
| JWT | 만료 | 인증 거부 | 통과 |
| JWT | 다른 secret 서명 | 인증 거부 | 통과 |
| API | Bearer 없음 | HTTP 401 | 통과 |
| API | 정상 Bearer | HTTP 200 및 회원 계약 | 통과 |
| API | 무효 Bearer | HTTP 401 | 통과 |
| 회원 | 숫자가 아닌 subject | HTTP 401 | 통과 |
| 회원 | 비활성 계정 | HTTP 401 | 통과 |
| 회원 | 삭제 프로필 | HTTP 401 | 통과 |
| 회원 | ACTIVE + PRIVATE 프로필 | 회원 정보 반환 | 통과 |

- 평가셋: 10건
- 합격 기준: 10/10 통과, 전체 회귀 실패 0건
- 결과: 10/10 통과, 전체 Gradle `BUILD SUCCESSFUL`

## Before / After

| 항목 | Before | After |
|---|---|---|
| 회원 표시 | `개발자님` 하드코딩 | DB의 실제 `users.name` |
| 보호 API | `/resume/**`만 인증 요구 | `/resume/**`, `/members/**` 인증 요구 |
| 계정 상태 | 프런트 토큰 존재만 확인 | 서버에서 ACTIVE 계정·비삭제 프로필 재검증 |
| 401 처리 | API별 처리 또는 미처리 | 공통 토큰 삭제·복귀 경로 보존 |

## 검증 명령

```text
gradlew.bat test
BUILD SUCCESSFUL in 18s

npm run lint
오류 0건

npm run build
vite v7.3.1, 1,827 modules transformed, build success

git diff --check
오류 0건
```

## 잔여 작업

- 배포 후 운영 `/api/v1/members/me`의 무토큰 401 및 실제 로그인 토큰 200 검증
- CH-R1-02~04 실제 브라우저 viewport·키보드·Google/GitHub 로그인 QA
- CH-P2 V4 Career Data Foundation 시작 전 Flyway 번호를 저장소와 Notion에 동기화
