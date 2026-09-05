# Company Core V5 마이그레이션 검증 보고서

> 작업: P3-01 `companies` / `company_members` DDL 및 migration test
> 검증일: 2026-09-05 KST
> 대상 DB: MySQL 26.7.0 (운영 `26.7.0-cloud`와 동일 계열)
> 운영 배포: 미수행

## 결과

Career Hub 문서의 Release/Product DoD와 CH-G1 완료 상태를 Multi-Provider 실행계획의 선행 조건과 비교해 첫 미완료 작업인 P3-01을 최우선으로 선정했다. V5는 채용 기업 조직과 사용자 N:M membership을 생성하며 기존 수집 도메인 `company_source_targets`와 자동 연결하지 않는다.

사업자번호는 원문 대신 SHA-256 hex 해시와 마스킹 값만 저장한다. 기업 권한은 `users.role`이 아니라 `company_members.role`을 source of truth로 사용한다.

## 변경 범위

- `V5__create_company_core.sql`
  - `companies`: 기업명, 사업자번호 hash/masked, 상태, 생성자, 감사 시각
  - `company_members`: 기업·사용자 N:M, 역할, membership 상태, 초대자, 가입 시각
- `CompanyCoreV5MigrationTest`
  - clean V1→V5
  - V4 fixture의 V5 무손실 업그레이드
  - 사업자번호 hash와 membership 중복, orphan FK, 생성자 삭제 차단, 기업 삭제 cascade
- `CareerHubV4MigrationTest`
  - V4 전용 검증 target을 `4`로 명시해 후속 migration과 격리
- `run-member-migration-tests.sh`
  - V5 전용 평가셋을 전체 MySQL 게이트에 포함

## KPI / OKR / 평가셋

- **목표 KPI**: MySQL 26.7 clean/V4 upgrade 성공률 100%, 기존 사용자·Career 데이터 손실 0건, 사업자번호 hash·membership 중복 생성 0건, orphan FK 0건.
- **OKR 연결**: 검증된 기업 담당자만 기업 채용 기능에 접근하는 기업회원 MVP 기반을 만들고 membership 기반 권한의 감사 가능성을 확보한다.
- **평가셋**: 공식 `mysql:26.7.0`, V1→V5 clean 1건, V4→V5 upgrade 1건, unique/FK/cascade 제약 1건, 기존 인증·Career migration/integration 회귀 54건.
- **Before**: Career Hub V4까지 존재하며 기업 조직·membership 테이블 0개.
- **After**: V5의 기업 Core 테이블 2개, unique 제약 2개와 사용자/기업 FK 4개가 적용됨.
- **합격 기준**: V5 전용 3/3, 전체 MySQL 게이트 57/57, 전체 Gradle 회귀 성공, diff whitespace 오류 0건, V1~V4 수정 0건.

## 검증 명령과 결과

```powershell
& '<GIT_BASH>' ops/db/run-member-migration-tests.sh
```

- 결과: `BUILD SUCCESSFUL in 1m 8s`
- MySQL 게이트: 57 tests, failures 0, errors 0, skipped 0
- V5 전용: 3/3 통과

```powershell
.\gradlew.bat clean test --no-daemon
git diff --check
```

- 전체 Gradle: `BUILD SUCCESSFUL in 43s`
- diff: whitespace 오류 0건. 사용자 소유 E2E 파일의 줄바꿈 경고만 존재하며 해당 파일은 수정하지 않음.

## 발견 및 조치

첫 MySQL 게이트는 기존 V4 테스트 2건이 latest migration을 V4로 가정해 실패했다. V4 전용 테스트의 Flyway target을 `4`로 고정한 뒤 전체 57/57이 통과했다. 운영 데이터와 운영 DB는 변경하지 않았다.

## 다음 작업

P3-02에서 `Company` / `CompanyMember` Entity·Repository와 마지막 활성 OWNER가 0명이 되는 역할 변경·탈퇴 차단 invariant를 구현한다. 운영 Secret 교체는 개발 세션 무효화 영향을 고려해 운영 전 최종 보안 게이트로 유지한다.
