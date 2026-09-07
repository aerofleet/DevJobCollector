# Company 검증 요청 및 관리자 심사 검증 보고서

> 작업: P4-01 V6 검증 DDL·도메인·관리자 승인/반려
> 검증일: 2026-09-08 KST
> 대상 DB: MySQL 26.7.0 (운영 `26.7.0-cloud`와 동일 계열)
> 운영 배포: 미수행

## 결과

기업 `ACTIVE OWNER`가 사업자등록 증빙을 제출하고 플랫폼 관리자가 승인 또는 반려하는 흐름을 구현했다. 제출·심사는 기업 행을 비관적 잠금한 로컬 트랜잭션에서 처리하며, 기업별 `PENDING` 요청은 한 건만 허용한다. 승인 시 기업은 `VERIFIED`, 반려 시 `REJECTED`가 되며 반려 후 재요청하면 다시 `PENDING_VERIFICATION`으로 전환된다.

증빙은 외부 URL이 아닌 불투명한 `evidence_object_key`만 저장한다. 요청 DTO 문자열과 API 응답에는 object key가 노출되지 않는다. 심사 결과에는 상태, 심사자, 심사 시각과 반려 사유를 보존하며 요청자·심사자 계정 삭제는 FK `RESTRICT`로 차단한다.

## API 계약

```text
POST /api/v1/companies/{companyId}/verification-requests
Authorization: Bearer <TOKEN>
Request: method=BUSINESS_REGISTRATION_DOCUMENT, evidenceObjectKey
Response 201: requestId, companyId, requestStatus, companyStatus, requestedAt, reviewedAt

POST /api/v1/admin/company-verification-requests/{requestId}/approve
Authorization: Bearer <PLATFORM_ADMIN_TOKEN>

POST /api/v1/admin/company-verification-requests/{requestId}/reject
Authorization: Bearer <PLATFORM_ADMIN_TOKEN>
Request: reason
```

주요 오류 코드는 `COMPANY_OWNER_REQUIRED`(403), `PLATFORM_ADMIN_REQUIRED`(403), `COMPANY_VERIFICATION_PENDING_EXISTS`(409), `COMPANY_VERIFICATION_ALREADY_REVIEWED`(409)다. 관리자 권한은 JWT claim만 신뢰하지 않고 현재 활성 회원의 DB `users.role`을 기준으로 판정한다.

## KPI / OKR / 평가셋

- **목표 KPI**: 승인·반려 시 요청/기업 상태 불일치 0건, 중복 `PENDING` 생성 0건, 비 OWNER 제출 및 비 관리자 심사 차단률 100%, 심사자·심사 시각·반려 사유 누락 0건, 증빙 object key API 응답·DTO 문자열 노출 0건.
- **OKR 연결**: 검증된 기업만 후속 기업 기능을 사용하게 하는 신뢰 경계를 마련해 기업회원 MVP의 허위 기업 등록 위험을 낮춘다.
- **평가셋**: 도메인·서비스·웹 회귀 18건, MySQL V6 migration 3건, MySQL 제출·승인·반려·재요청 트랜잭션 3건, 기존 회원·Career·기업을 포함한 MySQL 전체 70건, 전체 Gradle 회귀 216건.
- **Before**: 기업은 `PENDING_VERIFICATION`으로 생성됐지만 검증 요청 테이블과 제출·승인·반려 API가 각각 0개였고 `VERIFIED`로 전환하는 심사 경로가 없었다.
- **After**: V6 테이블 1개와 인증 API 3개가 추가됐고, 평가셋에서 상태 불일치·중복 pending·권한 우회·감사 필드 누락·증빙 응답 노출이 모두 0건이었다.
- **합격 기준**: P4-01 단위·웹 18/18, MySQL 신규 6/6, 전체 MySQL 70/70, 전체 Gradle 성공, diff whitespace 오류 0건.

## 검증 명령과 결과

```powershell
.\gradlew.bat test `
  --tests kr.itsdev.devjobcollector.company.CompanyVerificationDomainTest `
  --tests kr.itsdev.devjobcollector.company.CompanyVerificationServiceTest `
  --tests kr.itsdev.devjobcollector.controller.CompanyControllerSecurityTest `
  --tests kr.itsdev.devjobcollector.controller.CompanyVerificationAdminControllerSecurityTest `
  --no-daemon
```

- 도메인 상태 전이, OWNER·플랫폼 관리자 권한, 입력 검증, 인증 및 응답 비노출 회귀 18/18 통과.

```powershell
# 격리 MySQL 26.7.0에서 ops/db/run-member-migration-tests.sh와 동일 테스트 목록 실행
.\gradlew.bat test <migration-and-integration-test-filters> --no-daemon
```

- V6 clean migration·V5 upgrade·FK 3/3 통과.
- 제출·승인·반려·재요청 트랜잭션 3/3 통과.
- 기존 회원·Career Hub·기업 회귀를 포함한 전체 MySQL 70/70, failures/errors/skipped 0건.

```powershell
.\gradlew.bat test --no-daemon
git diff --check
```

- 전체 Gradle `BUILD SUCCESSFUL`(216건 중 환경 의존 통합 테스트 70건 skip, failures/errors 0건).
- whitespace 오류 0건. 사용자 소유 E2E 파일의 줄바꿈 경고만 존재하며 해당 파일은 수정하지 않았다.

## 발견 및 조치

첫 MySQL 실행에서 테스트 슬라이스가 시간 주입용 보조 생성자까지 감지해 서비스 생성에 실패했다. 운영 생성자에 `@Autowired`를 명시한 뒤 신규 MySQL 6/6과 전체 MySQL 70/70을 재실행했다.

로컬 Git Bash는 Windows Docker Desktop 대신 WSL의 비활성 Docker 소켓을 참조해 셸 스크립트를 직접 실행하지 못했다. 같은 이미지·환경변수·테스트 필터를 PowerShell의 Windows Docker 컨텍스트에서 실행했으며, 검증 후 임시 컨테이너를 제거했다. CI의 Linux Docker 실행 경로와 스크립트 내용은 변경하지 않았다.

## 다음 작업

P4-02에서 기업 상태와 `OWNER`, `ADMIN`, `RECRUITER`, `VIEWER` 역할 조합별 membership authorization 서비스를 구현하고 권한 매트릭스 평가셋을 추가한다.
