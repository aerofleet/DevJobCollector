# DJC Company Authorization P4-02 검증 보고서

> 검증일: 2026-09-08
> 범위: 활성 기업 membership의 역할·기업 상태 기반 권한 판정

## 구현 결과

`CompanyAuthorizationService`가 요청의 `companyId`와 인증 사용자 ID로 membership을 조회하고, `company_members.status`, `company_members.role`, `companies.status`를 순서대로 검증하도록 구현했다. 기업 역할은 JWT나 `users.role`에서 읽지 않는다.

일반 기업 기능은 `VERIFIED` 기업의 `ACTIVE` membership에만 허용한다. membership 부재·비활성 또는 역할 부족은 `COMPANY_ACCESS_DENIED`, 역할은 충족하지만 기업이 미검증 상태이면 `COMPANY_NOT_VERIFIED`를 HTTP 403으로 반환한다. 권한이 없는 사용자에게 기업 상태가 먼저 노출되지 않도록 역할 판정을 상태 판정보다 앞에 둔다.

## 권한 계약

| 권한 | OWNER | ADMIN | RECRUITER | VIEWER |
|---|---:|---:|---:|---:|
| 기업 조회 | O | O | O | O |
| 기업 수정 | O | O | X | X |
| 회원 조회 | O | O | O | O |
| 회원 초대 | O | O | X | X |
| ADMIN 지정 | O | X | X | X |
| RECRUITER 지정 | O | O | X | X |
| 회원 제거 | O | O | X | X |
| OWNER 변경 | O | X | X | X |
| 채용공고 생성·수정 | O | O | O | X |

기업 인증 요청은 P4-01에서 확정한 `ACTIVE OWNER` 전용 서비스와 기업 상태 전이를 그대로 사용하며, 이번 일반 기업 기능 매트릭스에 중복 포함하지 않았다.

## KPI / OKR / 평가셋

- **목표 KPI**: 역할 4개 × 기업 상태 5개 × 핵심 권한 10개의 판정 정확도 100%, 무권한 조합 차단률 100%.
- **OKR 연결**: 검증된 기업의 승인된 담당자만 채용·회원 기능을 사용하게 하여 기업 기능의 신뢰성과 감사 가능성을 확보한다.
- **평가셋**:
  - `OWNER|ADMIN|RECRUITER|VIEWER` × `PENDING_VERIFICATION|VERIFIED|REJECTED|SUSPENDED|CLOSED` × 핵심 권한 10개 = 200건.
  - 비활성 membership `INVITED|SUSPENDED|LEFT` 3건과 membership 부재 1건.
  - 공식 MySQL `26.7.0`의 허용 1건·미검증 차단 1건을 포함한 전체 DB 게이트 72건.
- **Before**: 중앙 authorization 서비스 0개, 역할×기업 상태 자동 평가 0건.
- **After**: 매트릭스 200/200 일치, 무권한 조합 176/176 차단, 비활성·부재 membership 4/4 차단, MySQL 전체 72/72 통과.
- **합격 기준**: 판정 불일치 0건, DB 통합 실패·오류·skip 0건, 전체 Gradle 회귀 실패·오류 0건.

## 검증 명령과 결과

```powershell
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.company.CompanyAuthorizationServiceTest" --tests "kr.itsdev.devjobcollector.controller.ApiExceptionHandlerTest" --tests "kr.itsdev.devjobcollector.company.CompanyRepositoryIntegrationTest"
```

- 결과: `BUILD SUCCESSFUL`.
- 타깃 단위·예외 평가 207건은 통과했고, 환경변수 기반 Repository 테스트는 이 실행에서는 의도적으로 skip됐다.

```text
mysql:26.7.0 + ops/db/run-member-migration-tests.sh와 동일한 17개 test suite
```

- 결과: 72 tests, failures 0, errors 0, skipped 0.
- 임시 MySQL 컨테이너는 검증 후 제거했다.

```powershell
.\gradlew.bat test
```

- 결과: `BUILD SUCCESSFUL`, 423 tests, failures 0, errors 0, 환경변수 기반 72 tests skip.
- `git diff --check`: whitespace 오류 0건.

## 남은 범위

P4-03에서 멤버 초대·역할 변경·제거 API에 이 서비스를 연결하고, 마지막 활성 OWNER invariant와 함께 실제 API 권한 경계를 검증한다.
