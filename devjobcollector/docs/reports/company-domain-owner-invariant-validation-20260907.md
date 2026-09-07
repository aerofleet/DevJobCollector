# Company 도메인 및 마지막 OWNER invariant 검증 보고서

> 작업: P3-02 `Company` / `CompanyMember` 도메인·Repository 및 마지막 활성 OWNER 보호
> 검증일: 2026-09-07 KST
> 대상 DB: MySQL 26.7.0 (운영 `26.7.0-cloud`와 동일 계열)
> 운영 배포: 미수행

## 결과

V5 `companies`와 `company_members`를 JPA 도메인으로 연결하고 기업 상태, membership 역할, membership 상태를 서로 다른 enum으로 모델링했다. 기업별 membership 변경은 먼저 `companies` 행을 비관적 쓰기 잠금으로 직렬화한 뒤 대상 membership을 잠그고 활성 OWNER 수를 판정한다. 따라서 역할 강등, 탈퇴, 정지로 마지막 활성 OWNER가 0명이 되는 변경은 `LAST_ACTIVE_COMPANY_OWNER`로 차단한다.

사업자번호 원문 컬럼은 도메인에 추가하지 않았다. `Company`는 64자리 SHA-256 hex와 마스킹 값만 허용하며, `company_members`를 기업 권한 source of truth로 유지한다.

## 변경 범위

- `company` 패키지
  - `Company`, `CompanyMember` Entity와 상태·역할 enum
  - 사업자번호 hash 및 필수 필드 도메인 검증
  - 기업·사용자 membership 조회, 활성 OWNER 집계, 비관적 잠금 Repository
  - 역할·상태 변경을 수행하는 `CompanyMembershipService`
- Company 평가셋
  - 도메인 단위 테스트 4건
  - 마지막 OWNER 서비스 단위 테스트 4건
  - MySQL Repository·제약·invariant 통합 테스트 3건
  - 두 활성 OWNER의 동시 강등 통합 테스트 1건
- `run-member-migration-tests.sh`
  - Company Repository 및 동시성 통합 테스트를 MySQL 게이트에 포함

## KPI / OKR / 평가셋

- **목표 KPI**: 마지막 활성 OWNER 제거 차단률 100%, 두 OWNER 동시 강등 후 활성 OWNER 1명 이상 유지율 100%, Company Repository 평가셋 오류율 0%, 기존 MySQL migration/integration 회귀 0건.
- **OKR 연결**: 기업 기능 MVP에서 소유자 없는 기업 발생을 막고 membership 기반 권한의 일관성과 감사 가능성을 확보한다.
- **평가셋**: 도메인·서비스 단위 8건, 공식 `mysql:26.7.0` Company 통합 4건, 기존 migration/integration 57건을 합친 MySQL 게이트 61건.
- **Before**: V5 테이블과 DB unique/FK 제약은 존재하지만 애플리케이션 Entity·Repository가 없고, 마지막 활성 OWNER 역할 변경·탈퇴를 막는 트랜잭션 경계가 없었다.
- **After**: 역할·상태 변경은 기업 행 잠금 아래 실행되며, 단일 OWNER 강등/탈퇴와 두 OWNER 동시 강등 평가셋에서 활성 OWNER 0건 상태가 발생하지 않았다.
- **합격 기준**: Company 단위 8/8, MySQL Company 4/4, 전체 MySQL 61/61, 전체 Gradle 성공, diff whitespace 오류 0건.

## 검증 명령과 결과

```powershell
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.company.*" --no-daemon
```

- Company 단위 테스트 8/8 통과. DB 환경변수 기반 통합 테스트는 이 명령에서 조건부 제외됐다.

```powershell
& '<GIT_BASH>' ops/db/run-member-migration-tests.sh
```

- 공식 MySQL 26.7 게이트 61/61 통과, failures/errors/skipped 0건.
- Company Repository·invariant 3/3, 동시 OWNER 강등 1/1 통과.

## 발견 및 조치

신규 MySQL 평가셋의 첫 실행에서는 서로 다른 테스트 클래스가 같은 고정 사업자번호 hash를 사용해 61건 중 1건이 fixture unique 충돌로 실패했다. Repository 테스트 hash를 독립 값으로 분리한 뒤 전체 61/61을 재실행해 통과했다. 제품 코드나 운영 데이터 변경은 없었다.

## 다음 작업

P3-03에서 `CompanySignupFacade`와 기업 가입 API를 구현한다. 기업과 최초 활성 OWNER membership은 하나의 로컬 트랜잭션으로 생성하고, 동일 사업자번호·membership 동시 요청은 DB unique 제약을 최종 기준으로 처리한다.
