# Career Hub 데이터 Entity/Repository 검증 결과

## 범위

- CH-P2-03: `resumes`, `job_bookmarks`, `job_view_history`, `applications` Entity/Repository
- 회원 소유 범위 조회, 북마크 중복 방지, 상태 전이용 도메인 메서드
- V4 스키마와 JPA 매핑의 MySQL 26.7 호환성

## KPI / OKR

- 목표 KPI
  - Career 데이터 4종 영속화·회원별 조회 통과율 100%
  - 다른 회원 데이터의 owner-scoped 조회 노출 0건
  - 동일 회원·동일 공고 북마크 중복 허용 0건
  - main 배포·Docker 검증 성공률 100%, 운영 회귀 API 오류 0건
- OKR 연결
  - Career Hub Product DoD의 저장 공고·지원 현황·최근 본 공고·이력서 MySQL 영속화 기반을 제공한다.
  - 후속 CH-P3/CH-P4에서 타 회원 데이터 접근 차단률 100%를 달성할 저장소 경계를 확정한다.

## 평가셋과 합격 기준

| 평가셋 | 조건 | 합격 기준 | 결과 |
|---|---|---|---|
| 도메인 단위 테스트 | 이력서 상태, 지원 상태·메모, 최근 조회 시각 | 3/3 통과 | 3/3 통과 |
| MySQL Repository 통합 | MySQL 26.7, V1→V4 적용 후 Career 데이터 4종 저장·조회 | 1/1 통과 | 통과 |
| 회원 격리 | 서로 다른 회원 2명의 데이터 조회 | 교차 노출 0건 | 0건 |
| 중복 제약 | 동일 회원·동일 공고 북마크 2회 저장 | 두 번째 저장 거부 | 거부 확인 |
| 전체 회귀 | `gradlew.bat test` | 실패 0건 | BUILD SUCCESSFUL |
| 배포 검증 | main Backend/Docker Actions | 2/2 성공 | 2/2 성공 |
| 운영 smoke | health, 공개 search, 무토큰 member API | 200, 200, 401 | 200, 200, 401 |

## Before / After

- Before: V4 테이블만 존재하고 애플리케이션에서 Career 데이터 4종을 영속화하거나 회원 범위로 조회할 JPA 계층이 없었다.
- After: Entity 4개, 상태 enum 2개, Repository 4개와 owner-scoped query가 추가됐고 MySQL 통합 평가셋에서 회원 격리와 중복 제약을 확인했다.

## 검증 증거

- 구현 커밋: `c8b48fd`
- 사전 검증 브랜치 Docker Actions: `32849241829`
- main Docker Actions: `32852535004`
- main Backend 배포 Actions: `32852535010`
- 운영 smoke: `/actuator/health` 200, `/api/v1/jobs/search` 200, `/api/v1/members/me` 무토큰 401

## 잔여 위험과 다음 작업

- Repository의 회원 범위 조회는 검증했지만 API 계층의 인증 사용자 강제와 IDOR 차단은 CH-P3/CH-P4에서 별도 검증한다.
- CH-P2-05에서 운영 데이터를 변경하지 않는 ownership/orphan/duplicate 집계 감사를 추가한다.
- 실제 로그인 토큰을 사용한 회원 프로필 200 및 브라우저 수동 QA는 아직 미완료다.
