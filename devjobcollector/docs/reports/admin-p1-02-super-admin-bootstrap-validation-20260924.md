# DJC 관리자 P1-02 초기 SUPER_ADMIN 검증

## 결과

기본 비활성화된 일회성 bootstrap runner를 구현했다. 관리자 저장소가 비어 있을 때만 `SUPER_ADMIN`을 생성하며, 비밀번호는 기존 BCrypt cost 12 encoder를 통해 hash만 저장한다. 계정과 `ADMIN_BOOTSTRAP` 감사 로그는 같은 트랜잭션에서 기록된다.

동일 SUPER_ADMIN 재실행은 no-op이고, 동일 이메일의 비-SUPER_ADMIN 또는 다른 관리자 존재 상태에서는 시작을 실패시킨다. 비밀번호는 환경변수와 파일 입력 중 정확히 하나만 허용하고 runner 종료 시 입력 char buffer를 0으로 덮는다.

## KPI / OKR / 평가셋

- **OKR 연결**: 운영 DB 직접 수정 없이 감사 가능한 최초 관리자 생성 경로를 제공한다.
- **목표 KPI**: 최초 계정 1행, 감사 1행, 재실행 추가 행 0개, 평문 비밀번호 DB·감사 로그 저장 0건.
- **평가셋**: 서비스·secret reader·runner 단위 8건, MySQL 26.7 관리자 저장소 통합 4건 중 bootstrap 시나리오 1건.
- **Before**: 관리자 테이블은 있으나 초기 계정 생성 경로와 운영 절차가 없음.
- **After**: one-shot runner, empty-store guard, BCrypt 저장, 감사 원자 기록, 비밀 제거 런북을 제공함.
- **합격 기준**: 단위 8/8, MySQL 통합 전체 4/4, 기존 전체 테스트 회귀 실패 0건.

## 검증 결과

| 검증 | 결과 |
|---|---|
| `gradlew.bat test --tests kr.itsdev.devjobcollector.admin.bootstrap.*` | 8/8 성공 |
| MySQL 26.7 `AdminSecurityRepositoryIntegrationTest` | 4/4 성공, skip/failure/error 0 |
| `gradlew.bat test` | 총 518, 실패 0, 환경 조건부 skip 102 |
| 최초 생성 | `SUPER_ADMIN/ACTIVE` 1행 |
| 비밀번호 저장 | 원문 불일치, BCrypt matches 성공 |
| 감사 | `ADMIN_BOOTSTRAP` 1행 |
| 동일 이메일 재실행 | `ALREADY_EXISTS`, 계정·감사 추가 0건 |
| 임시 MySQL 컨테이너 | 테스트 종료 후 제거 |

운영 절차는 `docs/runbooks/admin-super-admin-bootstrap.md`를 따른다. 실제 운영 계정 생성은 운영 담당자의 이메일·전달 채널·MFA 방식 결정 이후 별도 승인 하에 수행한다.
