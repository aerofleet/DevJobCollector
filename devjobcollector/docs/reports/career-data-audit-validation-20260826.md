# Career Hub V4 데이터 감사 검증 결과

## 범위

- CH-P2-05: `ops/db/audit-career-v4.sql`
- 회원·공고 orphan, 회원·공고 쌍 중복, 이력서/지원 상태, 조회 이력 횟수·시간 순서 감사
- 원문 식별자 없이 집계값만 반환하는 read-only 계약

## KPI / OKR

- 목표 KPI
  - 정상 데이터 false positive 0건
  - 주입한 결함 유형 검출률 100%: orphan 7종, duplicate 3종, 상태·값 이상 5종
  - 감사 쿼리 데이터 변경문 0개, 민감 원문 출력 0건
  - MySQL 26.7 clean V4/V3 upgrade와 감사 평가셋 성공률 100%
  - 운영 배포 후 health/search 오류 0건
- OKR 연결
  - Career Hub Product DoD의 회원별 데이터 소유권과 무결성을 배포 전 반복 검증할 수 있게 한다.
  - CH-P3/CH-P4 API 구현 전 데이터 기반 결함을 수치로 분리해 타 회원 데이터 접근 차단률 100% 목표를 지원한다.

## 평가셋과 합격 기준

| 평가셋 | 조건 | 합격 기준 | 결과 |
|---|---|---|---|
| 정상 fixture | 회원 2, 공고 2, Career 데이터 각 1 | defect metric 전부 0 | 통과 |
| orphan fixture | FK 검사 해제 후 회원 orphan 4종·공고 orphan 3종 주입 | 7/7 검출 | 7/7 |
| duplicate fixture | unique index drift 후 세 owner/job 중복 주입 | 3/3 검출 | 3/3 |
| 상태·값 fixture | 이력서 2, 조회 이력 2, 지원 상태 1 결함 주입 | 5/5 검출 | 5/5 |
| read-only/privacy 계약 | SQL 문장·출력 컬럼 정적 검사 | 변경문 0, 민감 컬럼 0 | 통과 |
| 사전 CI | MySQL 26.7 + amd64/arm64 | 전체 성공 | Actions `32864748869` 성공 |
| main CI/배포 | Docker 검증 + Backend 배포 | 2/2 성공 | `32866285746`·`32866285666` 성공 |
| 운영 smoke | health, 공개 search, 무토큰 member API | 200, 200, 401 | 200, 200, 401 |

## Before / After

- Before: V4 제약은 migration test로만 확인되며, 운영 데이터의 orphan·중복·상태 이상을 비파괴 집계하는 반복 가능한 감사가 없었다.
- After: 단일 `SELECT`가 19개 metric을 반환하고, 정상·결함 fixture 5개 테스트가 15개 주입 결함을 모두 검출한다.

## 검증 명령과 결과

```powershell
.\gradlew.bat test
```

- 결과: `BUILD SUCCESSFUL in 22s`
- MySQL 전용 실행: `ops/db/run-member-migration-tests.sh`에 `CareerHubV4AuditTest` 포함
- 구현 커밋: `66b9fe2`

## 잔여 위험과 다음 작업

- 집계 감사는 데이터 무결성을 확인하지만 API 인증·인가를 대신하지 않는다.
- CH-P3에서 북마크 API의 인증 사용자 강제, 다른 회원 접근 차단, 동시 중복 요청을 별도 평가한다.
- CH-R1 브라우저 수동 QA와 실제 로그인 토큰 `/members/me` 200 검증은 계속 대기 중이다.
