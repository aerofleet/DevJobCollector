# DJC Company Member Management P4-03 검증 보고서

> 검증일: 2026-09-09
> 범위: 기업 멤버 목록·초대·역할 변경·제거 API와 권한/OWNER invariant

## 구현 결과

P4-02 `CompanyAuthorizationService`를 기업 멤버 관리 트랜잭션에 연결하고 다음 API를 추가했다.

```text
GET    /api/v1/companies/{companyId}/members
POST   /api/v1/companies/{companyId}/members/invitations
PATCH  /api/v1/companies/{companyId}/members/{memberId}/role
DELETE /api/v1/companies/{companyId}/members/{memberId}
```

모든 쓰기 요청은 `companies` 행, 요청자 membership, 대상 membership 순서로 잠근다. 따라서 권한 판정 후 대상 변경 사이에 요청자 역할이나 기업 상태가 바뀌는 TOCTOU 구간을 줄이고, 기존 마지막 활성 OWNER 보호와 같은 잠금 순서를 유지한다.

삭제는 `company_members` 행을 물리 삭제하지 않고 `LEFT`로 전환한다. 동일 사용자를 재초대하면 기존 `LEFT` 행을 `INVITED`로 재사용하므로 `(company_id, user_id)` unique 제약과 이력 연결을 보존한다. 일반 목록에서는 `LEFT` membership을 제외한다.

## 권한·오류 계약

- OWNER: 모든 역할 초대·변경, 모든 멤버 제거 가능. 단 마지막 활성 OWNER 제거·강등은 차단한다.
- ADMIN: RECRUITER/VIEWER 초대·역할 변경과 비-OWNER 멤버 제거 가능.
- ADMIN이 기존 OWNER/ADMIN을 우회 강등하지 못하도록 대상의 현재 역할과 새 역할을 모두 판정한다.
- RECRUITER/VIEWER, 비활성 membership, 미검증 기업은 P4-02 계약에 따라 차단한다.
- 주요 오류: `COMPANY_ACCESS_DENIED` 403, `COMPANY_NOT_VERIFIED` 403, `COMPANY_MEMBER_NOT_FOUND` 404, `COMPANY_INVITEE_NOT_FOUND` 404, `COMPANY_MEMBER_ALREADY_EXISTS` 409, `COMPANY_MEMBER_INACTIVE` 409, `LAST_ACTIVE_COMPANY_OWNER` 409.
- 초대 요청의 이메일은 DTO `toString()`에서 `<redacted>` 처리한다.

## KPI / OKR / 평가셋

- **목표 KPI**: 멤버 관리 핵심 API 4개 구현률 100%, 역할·상태 무권한 접근 차단률 100%, 마지막 활성 OWNER 손실 0건, 재초대 중복 행 0건.
- **OKR 연결**: 검증된 기업의 승인된 운영자만 담당자와 채용 권한을 관리하게 하여 기업 기능의 신뢰성과 감사 가능성을 확보한다.
- **평가셋**:
  - 서비스: 목록, 역할별 초대, 중복/DB race, LEFT 재초대, 역할 계층, 마지막 OWNER, 상태 전환, 비활성 초대 대상 10건.
  - Web/API: 인증된 목록·초대·역할 변경·제거와 입력 검증 5건.
  - MySQL 26.7: OWNER/ADMIN 계층, 중복 초대, 역할 변경, 제거·재초대, 마지막 OWNER, 미검증 기업 6건.
  - 기존 migration/Career/auth/company 전체 MySQL 게이트 78건.
- **Before**: 기업 멤버 관리 API 0개, LEFT 재초대 경로 0개, API 수준 OWNER 보호 응답 0개.
- **After**: API 4/4, P4-03 MySQL 6/6, 전체 MySQL 78/78, 전체 Gradle 446건 failures/errors 0.
- **합격 기준**: 신규 평가 불일치 0건, 무권한 변경 0건, 마지막 활성 OWNER 손실 0건, 재초대 후 membership 행 증가 0건, 전체 회귀 실패 0건.

## 검증 명령과 결과

```powershell
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.company.CompanyMemberManagementServiceTest" --tests "kr.itsdev.devjobcollector.company.CompanyAuthorizationServiceTest" --tests "kr.itsdev.devjobcollector.controller.CompanyControllerSecurityTest" --tests "kr.itsdev.devjobcollector.controller.ApiExceptionHandlerTest"
```

- 결과: `BUILD SUCCESSFUL`, 229 tests, failures 0.

```text
mysql:26.7.0 + ops/db/run-member-migration-tests.sh의 18개 test suite
```

- 결과: 78 tests, failures 0, errors 0, skipped 0.
- P4-03 신규 통합 평가: 6/6.
- 임시 MySQL 컨테이너는 검증 후 제거했다.

```powershell
.\gradlew.bat test
```
- 결과: `BUILD SUCCESSFUL`, 446 tests, failures 0, errors 0, 환경변수 기반 78 tests skip.
- `git diff --check`: whitespace 오류 0건.

## 발견과 조치

1. 첫 타깃 실행은 테스트 JSON text block 문법 오류로 컴파일이 중단됐다. 입력을 일반 JSON 문자열로 수정했다.
2. 다음 타깃 실행은 Mockito fixture의 중첩 stubbing으로 229건 중 1건 실패했다. fixture를 stubbing 전에 생성해 해결했다.
3. 첫 MySQL 전체 실행은 신규 6건 모두 Spring 생성자 선택 실패로 실행되지 않았다. 운영 생성자에 명시적 주입을 적용했다.
4. 다음 MySQL 신규 실행은 트랜잭션 밖 fixture의 detached entity로 6건 중 4건 실패했다. 사용자·프로필·membership fixture를 로컬 트랜잭션으로 묶은 뒤 신규 6/6과 전체 78/78을 통과했다.

## 남은 범위

다음 단계는 P5-01 기업 가입·검증 상태 프론트엔드다. P6에서 초대 rate limit, 역할 변경·제거 감사 이벤트, metrics와 동시성 확대 평가를 추가한다.
