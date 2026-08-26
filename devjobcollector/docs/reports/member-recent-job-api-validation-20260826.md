# CH-P3-02 회원 최근 본 공고 API 검증

## 결과

인증 회원 전용 최근 본 공고 기록·조회 API를 구현하고 운영에 배포했다.

- `POST /api/v1/members/me/recent-jobs/{jobPostId}`
- `GET /api/v1/members/me/recent-jobs`
- 동일 회원·공고 재조회는 새 행을 만들지 않고 `last_viewed_at`과 `view_count`를 원자적으로 갱신한다.
- 회원별 최근 조회 기록은 `last_viewed_at DESC, id DESC` 기준 최대 100개를 보존한다.
- 기록 트랜잭션은 회원 행을 잠근 뒤 upsert와 초과 행 삭제를 수행한다.
- 공개 공고 상세 API의 비로그인 접근 계약은 변경하지 않았다.

## KPI / OKR

- 목표 KPI
  - 동일 회원·공고 중복 행 0건
  - 재조회 시 `view_count` 증가 성공률 100%
  - 회원별 보존 한도 초과 행 0건
  - 비인증 API 차단률 100%
  - 타 회원 조회 이력 노출 0건
  - 전체 회귀 실패·오류 0건
- OKR 연결
  - Objective: 공개 채용 탐색을 로그인 회원의 지속적인 커리어 관리 여정으로 연결한다.
  - KR2의 북마크·지원·최근 조회 데이터 유실 0건과 KR3의 무권한 접근 허용 0건에 기여한다.

## 평가셋과 결과

| 평가셋 | 합격 기준 | 결과 |
|---|---|---|
| 서비스 기록·목록·404·보존 한도 | 4/4 성공 | 4/4 성공 |
| 컨트롤러 인증·GET·POST | 3/3 성공, 무토큰 허용 0건 | 3/3 성공 |
| MySQL 26.7 동일 공고 2회 upsert | 행 1개, `view_count=2`, 최초 시각 보존 | 성공 |
| 보존 정책 fixture | 최신 100개 보존, 101번째 이후 제거 | 103개 입력 기준 100개 보존·3개 제거 |
| 로컬 전체 Gradle | 실패·오류 0건 | 124건 중 76건 실행 성공, MySQL 환경 의존 48건 skip |
| Actions MySQL 26.7 | 조건부 평가셋 skip 0, 단계 성공 | `32948243993`, `32948243996` 성공 |
| 멀티아키텍처 이미지 | amd64/arm64 2/2 성공 | 2/2 성공 |
| 운영 smoke | health/search 200, 최근 조회 GET/POST 무토큰 401 | 4/4 성공 |

## Before / After

| 항목 | Before | After |
|---|---|---|
| 최근 본 공고 API | 없음 | 기록·최신순 조회 2개 |
| 동일 공고 재조회 | 영속 동작 없음 | 단일 행 유지, 조회 횟수 증가 |
| 보존 한도 | 미정 | 회원당 최신 100개 |
| 동시 기록 직렬화 | 없음 | 회원 행 잠금 후 upsert·prune |
| 비인증 접근 | 실제 API 없음 | GET/POST 모두 HTTP 401 |

## 검증 명령

```powershell
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.career.JobViewHistoryServiceTest" --tests "kr.itsdev.devjobcollector.controller.JobViewHistoryControllerSecurityTest"
.\gradlew.bat test
gh run view 32948243993 --repo aerofleet/DevJobCollector --json jobs,conclusion,url
gh run view 32948243996 --repo aerofleet/DevJobCollector --json jobs,conclusion,url
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/actuator/health
curl.exe -sS -o NUL -w "%{http_code}" "https://<API_DOMAIN>/api/v1/jobs/search?size=1"
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/api/v1/members/me/recent-jobs
curl.exe -sS -o NUL -w "%{http_code}" -X POST https://<API_DOMAIN>/api/v1/members/me/recent-jobs/1
```

## 합격 판단과 잔여 항목

- CH-P3-02 코드·MySQL 26.7·배포·무토큰 보안 smoke 기준은 합격이다.
- 실제 로그인 토큰으로 POST 후 GET 응답을 확인하는 운영 happy path는 운영 계정 QA 항목으로 남긴다.
- CH-P3-06의 동시 요청 20회·concurrency 10 평가 전까지 고부하 동시성 최종 게이트는 완료로 표시하지 않는다.
- 다음 독립 구현은 CH-P3-03 지원 상태 조회·변경 API다.
