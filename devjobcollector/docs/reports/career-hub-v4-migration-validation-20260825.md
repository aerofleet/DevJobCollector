# Career Hub V4 데이터 기반 검증 결과

> 검증일: 2026-08-25 KST  
> 범위: CH-P2-01, CH-P2-02, CH-P2-04  
> 운영 반영: 완료 — 커밋 `3fef55b`, Backend Actions `32842721029`

## 결과

- Flyway 순서를 Career Hub V4, 기업 Core V5, 기업 검증 V6으로 확정했다.
- V4에 `resumes`, `job_bookmarks`, `job_view_history`, `applications` 4개 테이블을 추가했다.
- 회원 삭제 시 Career 데이터가 정리되며, 공고별 북마크·조회 이력·지원은 회원당 1건으로 제한한다.
- 이력서는 복수 생성을 허용하고 JSON 본문, 상태, 생성·수정 시각을 저장한다.
- V1/V2/V3 SQL과 기존 운영 데이터는 수정하지 않았다.

## KPI / OKR

- 목표 KPI
  - MySQL 26.7 clean V1→V4 성공률 100%
  - 기존 V3→V4 upgrade 성공률 100%
  - 기존 회원·공고 데이터 보존률 100%
  - 중복 북마크 허용 0건, 잘못된 FK 허용 0건
  - 운영 health/search 회귀 성공률 100%
- OKR 연결
  - Objective: 회원의 채용 활동과 이력서를 재시작 후에도 보존하는 Career Hub 기반을 만든다.
  - KR: Career 데이터 유실 0건, 타 회원 데이터 접근 허용 0건을 위한 소유권 FK 기반 확보.

## 평가셋

| 조건 | 기대 결과 | 결과 |
|---|---|---|
| MySQL 26.7 clean V1→V4 | schema version 4, 신규 테이블 4개 | 통과 |
| 기존 V3 + 회원·공고 fixture → V4 | 기존 행 보존, Career 행 자동 생성 0 | 통과 |
| 동일 회원·공고 북마크 중복 | DB unique 차단 | 통과 |
| 조회 횟수 0 | CHECK constraint 차단 | 통과 |
| 존재하지 않는 회원 지원 | FK 차단 | 통과 |
| 회원 삭제 | 소유 Career 행 4종 cascade 삭제 | 통과 |
| 전체 일반 Gradle 회귀 | failure 0 | 통과 |
| amd64/arm64 이미지 빌드 | 양쪽 성공 | 통과 |

- 합격 기준: 위 8개 조건 100%, 운영 health/search 200, 보호 API 무토큰 401
- 결과: 8/8 통과, 운영 health 200, search 200, `/members/me` 401

## Before / After

| 항목 | Before | After |
|---|---|---|
| 이력서 | JVM 메모리 저장 | MySQL V4 저장 기반 |
| 북마크·조회·지원 | 저장 테이블 없음 | 회원·공고 FK 및 중복 방지 테이블 |
| Flyway 예약 | Career/기업 번호 충돌 | Career V4, 기업 V5/V6 |
| 배포 게이트 | V3까지만 검사 | clean V4와 V3 upgrade 검사 |

## 검증 증거

- 검증 브랜치 Docker CI: `32841106449` 성공
- main Docker CI: `32842721118` 성공
- 운영 Backend deploy: `32842721029` 성공
- 로컬 `gradlew.bat test`: `BUILD SUCCESSFUL in 18s`
- 운영 smoke: health 200, 공개 검색 200, 회원 API 무토큰 401

## 잔여 작업

- CH-P2-03 Entity/Repository와 상태 enum 구현
- CH-P2-05 ownership/orphan/duplicate read-only audit 구현
- 운영 DB 직접 schema version·신규 테이블 4개 aggregate 확인은 비식별 감사 경로에서 수행
