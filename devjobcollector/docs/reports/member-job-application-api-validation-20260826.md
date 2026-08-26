# CH-P3-03 회원 지원 상태 API 검증

## 결과

인증 회원 전용 지원 생성·최신순 조회·상태 변경 API를 구현하고 운영에 배포했다.

- `POST /api/v1/members/me/applications/{jobPostId}`: 최초 상태 `APPLIED`, 선택 메모 최대 1000자
- `GET /api/v1/members/me/applications`: 현재 회원 소유 지원만 최신 수정순 조회
- `PATCH /api/v1/members/me/applications/{applicationId}/status`: 소유자 범위에서 상태 변경
- 중복 POST는 기존 지원 행과 상태·메모를 유지하는 멱등 동작이다.
- 지원 ID가 없거나 다른 회원 소유면 동일하게 HTTP 404를 반환한다.

## KPI / OKR

- 목표 KPI: 동일 회원·공고 중복 행 0건, 타 회원 상태 변경 허용 0건, 무토큰 허용 0건, 유효 상태 변경 성공률 100%, 전체 회귀 실패·오류 0건.
- OKR 연결: Career Hub KR2의 지원 데이터 유실 0건과 KR3의 무권한 접근 허용 0건에 기여한다.

## 평가셋과 결과

| 평가셋 | 합격 기준 | 결과 |
|---|---|---|
| 서비스 생성·중복·목록·상태·소유권 | 5/5 성공 | 5/5 성공 |
| 컨트롤러 인증·GET·POST·PATCH | 무토큰 허용 0건, 정상 직렬화 | 성공 |
| MySQL 26.7 중복 생성 | 2회 요청 후 1행, 최초 메모·상태 보존 | 성공 |
| 로컬 전체 Gradle | 실패·오류 0건 | 132건 중 83건 실행 성공, 환경 의존 49건 skip |
| Actions MySQL 26.7·멀티아키텍처 | 모든 단계 성공 | `32957389993`, `32957390045` 성공 |
| 운영 smoke | health/search 200, applications GET/POST/PATCH 무토큰 401 | 5/5 성공 |

## Before / After

| 항목 | Before | After |
|---|---|---|
| 지원 현황 API | 없음 | 생성·조회·상태 변경 3개 |
| 중복 생성 | DB 유일키만 존재 | 멱등 POST, 기존 상태·메모 보존 |
| 소유권 변경 | 실제 API 없음 | `applicationId + current userId` 조회 |
| 지원 상태 | 도메인 enum만 존재 | API에서 6개 상태 직렬화·변경 |

## 검증 명령

```powershell
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.career.JobApplicationServiceTest" --tests "kr.itsdev.devjobcollector.controller.JobApplicationControllerSecurityTest"
.\gradlew.bat test
gh run view 32957389993 --repo aerofleet/DevJobCollector --json jobs,conclusion,url
gh run view 32957390045 --repo aerofleet/DevJobCollector --json jobs,conclusion,url
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/actuator/health
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/api/v1/members/me/applications
```

## 합격 판단과 잔여 항목

- CH-P3-03 코드·MySQL 26.7·배포·무토큰 보안 smoke 기준은 합격이다.
- 실제 로그인 토큰 생성·조회·상태 변경 happy path는 운영 계정 QA 항목으로 남긴다.
- concurrency 10에서 중복 POST 20회 평가는 CH-P3-06에서 수행한다.
- 다음 독립 구현은 CH-P3-04 `/my-devjobs` 실제 데이터 연동이다.
