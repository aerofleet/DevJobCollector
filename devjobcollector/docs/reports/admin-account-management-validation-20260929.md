# 관리자 계정 관리 검증 (2026-09-29)

## 범위

- `SUPER_ADMIN` 세션 전용 목록·상세·계정 생성·역할 변경·비활성화/재활성화 API와 화면.
- 신규 계정은 `ADMIN` 또는 `REVIEWER`로 생성한다. 32자리 Base32 TOTP 비밀키를 암호화해 저장하고 비밀번호는 해시만 저장한다. 생성 응답과 감사 기록에 두 비밀값을 포함하지 않는다.
- 최고 관리자 자신과 다른 `SUPER_ADMIN` 계정 변경을 차단한다. 변경 시 기대 버전·사유를 확인하고 활성 세션 폐기와 감사 기록을 한 트랜잭션에서 처리한다.
- 초기 비밀번호와 MFA 비밀키 전달은 최고 관리자가 별도 보안 채널에서 수행한다. 생성 화면을 닫으면 클라이언트 입력값을 지운다.

## KPI / OKR / 평가셋

| 항목 | 기준 |
|---|---|
| OKR | 관리자 계정·권한 운영에서 수동 DB 조작 0건을 달성한다. |
| 목표 KPI | 일반 회원 토큰 및 비최고 관리자 관리 API 허용 0건; 비밀값 응답 노출 0건; 상태·역할 변경 성공 시 활성 세션 미폐기 0건, 감사 누락 0건; 목록 p95 ≤ 500ms. |
| 평가셋 | MySQL 26.7 전체 DB 회귀와 신규 계정 생성→역할 변경→비활성화→재활성화, 역할 거부·자기 계정 보호; 관리자 웹 인증/CSRF/버전 3건; 360/768/1024/1440px E2E. 목록 warm-up 3회·측정 30회. |
| Before → After | 계정 관리 API 0개·화면 자리 표시자 1개 → API 5개·관리 화면 1개. 계정 변경 시 활성 세션 폐기·감사 평가 0건 → 신규 평가셋에서 모두 확인. |
| 합격 기준 | 회귀 실패 0건, 위 보안 KPI 전부 충족, 4개 viewport E2E 성공, 목록 p95 ≤ 500ms. |

## 검증 결과

- `./gradlew.bat compileJava --offline`: 통과.
- `./gradlew.bat test --offline --tests kr.itsdev.devjobcollector.admin.accounts.AdminAccountManagementControllerSecurityTest`: 통과.
- `npm.cmd run lint`, `npm.cmd run build`: 통과.
- `bash ops/db/run-member-migration-tests.sh`: MySQL 26.7 회귀 111/111 통과. 관리자 계정 목록 p95 6ms(단일 신규 계정 fixture, 30회), 기준 500ms 이내.
- `ADMIN_E2E_EXTERNAL_SERVER=true npm.cmd run e2e`: 29 통과, 대상 viewport 외 3건 의도된 건너뜀, 실패 0. 신규 계정 생성·역할 변경 시나리오 4개 viewport 통과.
- 커밋 `d666977`: 관리자 Workers `36588830163`, 백엔드 `36588830115`, Docker CI `36588830217` 모두 성공(2026-09-30).
- 운영 smoke: 관리자 `/admins` 직접 경로 200; API health 200; 무인증 계정 목록·상세 각각 401; 관리자 Origin의 POST OPTIONS 200 및 허용 Origin 확인.

## 남은 작업

- 분실 계정의 암호·MFA 재설정과 초기 자격 정보 전달 정책을 별도 운영 절차로 확정한다.
- 역할별 실제 계정으로 운영 QA와 기존 세션 무효화를 확인한다.
