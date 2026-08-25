# 회원 북마크 API 검증 결과

## 범위와 API 계약

- `POST /api/v1/members/me/bookmarks/{jobPostId}`: 인증 회원의 북마크를 멱등 생성하고 현재 값을 반환
- `GET /api/v1/members/me/bookmarks`: 인증 회원의 북마크를 최신순 조회
- `DELETE /api/v1/members/me/bookmarks/{jobPostId}`: 인증 회원·공고 쌍만 멱등 삭제, HTTP 204
- 응답: bookmark/job ID, 기업명, 공고명, 지역, 경력, 마감일, 원문 URL, 저장 시각

## KPI / OKR

- 목표 KPI
  - 무토큰 북마크 API 차단률 100%
  - 동일 회원·공고 순차 중복 생성 후 저장 행 1개
  - 타 회원 데이터가 목록·삭제 조건에 포함되는 경우 0건
  - 알 수 없는 공고 생성 시 DB 쓰기 0건, HTTP 404
  - 목록의 공고 연관 N+1 조회 0건(`EntityGraph` 1회 조회)
  - 운영 배포 후 health/search 회귀 오류 0건
- OKR 연결
  - `/my-devjobs`의 저장 공고를 Empty State가 아닌 회원별 실제 데이터로 전환하는 첫 API를 제공한다.
  - Career Hub Product DoD의 타 회원 Career 데이터 접근 차단률 100% 목표에 기여한다.

## 평가셋과 합격 기준

| 평가셋 | 합격 기준 | 결과 |
|---|---|---|
| 서비스 단위 4건 | 생성·목록·삭제 owner scope, unknown job 404 | 4/4 통과 |
| Controller 보안 3건 | GET/POST/DELETE 무토큰 401, 인증 subject 전달 | 3/3 통과 |
| MySQL Repository | 같은 owner/job insert 2회 후 행 1개 | 통과 |
| 로컬 전체 Gradle | 실행 테스트 failure/error 0 | 116개 중 실행 69, skip 47, failure/error 0 |
| MySQL 26.7 사전 CI | migration/audit/repository 및 multi-arch 성공 | `32879495958` 성공 |
| main CI/배포 | Backend/Docker 2/2 성공 | `32880026361`·`32880026369` 성공 |
| 운영 smoke | health 200, search 200, 북마크 무토큰 401 | 200/200/401 |

## Before / After

- Before: `job_bookmarks` Entity/Repository만 있고 HTTP API와 인증 회원 경계가 없었다.
- After: 회원 재검증을 통과한 subject만 생성·목록·삭제할 수 있으며, MySQL `ON DUPLICATE KEY`와 unique key로 재시도 시 행 수를 1개로 유지한다.

## 검증 명령과 결과

```powershell
.\gradlew.bat test
```

- 결과: `BUILD SUCCESSFUL in 20s`
- 구현 커밋: `ff5590f`
- 사전 CI: MySQL 26.7 clean V4/V3 upgrade와 amd64/arm64 build 성공

## 잔여 위험과 다음 작업

- 실제 로그인 토큰을 사용한 운영 생성·조회·삭제는 smoke 계정 자격증명이 없어 수행하지 않았다.
- 동시 10개 요청·20회 중복 평가셋은 CH-P3-06에서 실행한다. 현재 구현은 DB unique key와 원자적 upsert를 사용한다.
- 다음 작업은 CH-P3-02 최근 본 공고 기록·조회·보존 한도 정책이다.
