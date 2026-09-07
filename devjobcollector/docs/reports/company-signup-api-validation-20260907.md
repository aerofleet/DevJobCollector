# Company 가입 Facade 및 API 검증 보고서

> 작업: P3-03 `CompanySignupFacade` 및 기업 가입 API
> 검증일: 2026-09-07 KST
> 대상 DB: MySQL 26.7.0 (운영 `26.7.0-cloud`와 동일 계열)
> 운영 배포: 미수행

## 결과

인증된 활성 회원이 `POST /api/v1/companies`로 기업을 등록하는 API를 구현했다. `CompanySignupFacade`는 현재 회원 확인, 사업자번호 보호 처리, `PENDING_VERIFICATION` 기업 생성, 최초 `ACTIVE OWNER` membership 생성을 하나의 로컬 트랜잭션에서 수행한다. membership 저장이 실패하면 앞서 저장한 기업도 롤백된다.

사업자번호 요청값은 10자리 형식만 허용하고 하이픈 제거 후 SHA-256 hex와 `***-**-12345` 형식의 마스킹 값으로 변환한다. 원문은 Entity·응답·예외 메시지에 포함하지 않는다. 동일 사업자번호는 사전 조회와 DB unique 충돌 양쪽에서 `COMPANY_ALREADY_EXISTS` HTTP 409로 정규화한다.

## API 계약

```text
POST /api/v1/companies
Authorization: Bearer <TOKEN>

Request: legalName, displayName, businessNumber, websiteUrl(optional)
Response 201: companyId, companyStatus, membershipId, role, membershipStatus
Conflict 409: COMPANY_ALREADY_EXISTS
Unauthenticated: 401
```

생성 직후 상태는 `companyStatus=PENDING_VERIFICATION`, `role=OWNER`, `membershipStatus=ACTIVE`다. P4에서 기업 검증 상태에 따른 실제 기업 기능 권한을 별도로 제한한다.

## KPI / OKR / 평가셋

- **목표 KPI**: 정상 기업+OWNER 원자 생성 성공률 100%, membership 실패 시 불완전 기업 0건, 동일 사업자번호 중복 기업·membership 0건, 무토큰 생성 차단률 100%, 사업자번호 원문 응답·예외 노출 0건.
- **OKR 연결**: 검증 대기 기업과 최초 책임자를 일관된 상태로 생성해 기업회원 MVP 가입 경로 및 후속 검증 워크플로의 기반을 확보한다.
- **평가셋**: P3-03 단위·웹 테스트 10건, 공식 MySQL 26.7 원자성 통합 3건, 기존 migration/integration 포함 MySQL 전체 64건, 전체 Gradle 회귀 1회.
- **Before**: V5 Entity·Repository와 OWNER 보호는 존재하지만 기업 가입 Facade/API가 없어 기업과 최초 OWNER를 생성할 애플리케이션 경로가 없었다.
- **After**: 인증 API 1개, 정상·중복·강제 membership 실패 DB 시나리오 3개가 존재하며 기업만 남거나 OWNER가 중복되는 결과는 0건이다.
- **합격 기준**: P3-03 단위·웹 10/10, MySQL 가입 3/3, 전체 MySQL 64/64, 전체 Gradle 성공, diff whitespace 오류 0건.

## 검증 명령과 결과

```powershell
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.company.*" `
  --tests "kr.itsdev.devjobcollector.controller.CompanyControllerSecurityTest" `
  --tests "kr.itsdev.devjobcollector.controller.ApiExceptionHandlerTest" --no-daemon
```

- Facade·사업자번호 보호·Controller 인증/검증·409 계약 통과.

```powershell
& '<GIT_BASH>' ops/db/run-member-migration-tests.sh
```

- 공식 MySQL 26.7 전체 64/64, failures/errors/skipped 0건.
- P3-03 성공 생성·중복·강제 rollback 3/3 통과.

```powershell
.\gradlew.bat clean test --no-daemon
git diff --check
```

- 전체 Gradle `BUILD SUCCESSFUL`.
- whitespace 오류 0건. 사용자 소유 E2E 파일의 줄바꿈 경고만 존재하며 해당 파일은 수정하지 않았다.

## 발견 및 조치

첫 MySQL 실행은 기존 비트랜잭션 동시성 fixture가 남아 있는 상태에서 전체 `companies` 건수를 1로 가정해 1건 실패했고, trigger가 발생시킨 SQLSTATE `45000`이 예상한 구체 예외 대신 `JpaSystemException`으로 번역돼 1건 실패했다. assertion을 신규 company ID/hash 범위로 격리하고 Spring `DataAccessException` 상위 계약으로 맞춘 뒤 전체 64/64를 재실행했다. 제품 코드나 운영 데이터 변경은 없었다.

## 다음 작업

P4-01에서 V6 `company_verification_requests` DDL·도메인과 관리자 승인/반려를 구현한다. 증빙 원문 URL이나 사업자번호 원문은 로그·응답에 남기지 않고, 검증 상태 전이와 감사 이벤트 누락률 0%를 평가한다.
