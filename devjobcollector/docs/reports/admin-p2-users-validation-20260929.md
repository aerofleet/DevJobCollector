# DJC 관리자 P2 회원 관리 구현 검증

## 범위

- 관리자 세션 전용 회원 검색·페이지 목록, 회원 상세, 상태 변경 API와 SPA 화면
- `ACTIVE` ↔ `SUSPENDED` 전이, 사유·기대 버전 확인, 사용자 상태 이력과 관리자 감사의 단일 트랜잭션 기록
- 상태 변경 시 `session_revoked_at`을 기록하고 기존 회원 JWT를 인증 필터에서 거부

## KPI / OKR / 평가셋

- **OKR 연결**: 회원 상태 조치의 운영 DB 수동 변경 0건.
- **목표 KPI**: 회원 목록 API p95 ≤ 500ms, 상태 변경 성공 건의 이력·감사 기록률 100%, 정지·해제 이전 JWT 재사용 허용 0건.
- **평가셋**: 서비스 정상·버전 충돌·역할 거부 3건, 정지·해제 전 JWT 거부 2건, 관리자 세션·일반 회원 JWT·CSRF·버전 필수 보안 4건, MySQL 26.7 V9 clean/upgrade 2건과 회원 영속성·목록 1건, 전체 DB 평가 106건, SPA 360/768/1024/1440px 회원 정지 E2E 4건.
- **Before / After**: 회원 관리 화면은 안내 자리 표시자였고 API는 0개였다. 변경 후 목록·상세·상태 변경 API 3개와 회원 관리 화면 1개가 구현됐다. 목록 p95는 구현 전 측정 대상 없음 → MySQL 26.7 단일 fixture 30회에서 6ms.
- **합격 기준**: 백엔드·프런트 회귀 실패 0건, 4개 viewport 회원 흐름 통과, MySQL 26.7 목록 p95 ≤ 500ms, 상태·이력·감사 원자성 검증 통과.

## 로컬 결과

- `./gradlew.bat test --offline`: 550 tests, failures 0, errors 0, DB 조건부 skip 106.
- `ops/db/run-member-migration-tests.sh`: MySQL 26.7 평가 106/106 통과. V9 clean/upgrade 2/2, 회원 상태·이력·감사·폐기 시각 1/1, 회원 목록 30회 p95 6ms.
- `npm.cmd run lint`, `npm.cmd run build`: 통과.
- `ADMIN_E2E_EXTERNAL_SERVER=true npm.cmd run e2e`: 13 passed / 비대상 viewport 3 skipped. 회원 정지 확인 패널을 포함한 4개 viewport 검증 통과.
- 관리자 상태 변경 PATCH의 CORS 누락을 보안 테스트에서 검출해 허용 메서드와 preflight 평가를 추가했다.
- `git diff --check`: 오류 0건.

## 남은 게이트

- 실제 로그인 발급 토큰의 정지·해제 전후 재사용 거부 확인.
- 동시 상태 변경 20회 평가.

## 운영 배포 확인

- 커밋 `5e83a14`; Admin Workers `36556611600`, Backend `36556611569`, Docker `36556611696` 모두 성공.
- 운영 관리자 `/users` HTTP 200, API health HTTP 200, 무인증 `GET /api/v1/admin/users` HTTP 401.
- 배포 번들에 회원 목록·상태 변경 요청 코드 포함. 관리자 Origin의 상태 변경 PATCH preflight HTTP 200이며 `PATCH`와 `x-csrf-token` 허용.
- 실제 관리자 로그인 세션을 사용한 상태 변경은 운영 데이터 변경을 수반하므로 이 smoke에서는 실행하지 않았다.
