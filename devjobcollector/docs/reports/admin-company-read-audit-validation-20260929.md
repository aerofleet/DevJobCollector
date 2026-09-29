# 관리자 기업 조회·감사 기록 검색 검증 (2026-09-29)

## 범위와 판단

- 기업 목록: 이름·상태 검색, 서버 페이지네이션. 상세: 마스킹된 사업자번호 및 최근 인증 요청 상태·시각·반려 사유.
- 증빙 원본을 열람할 저장소/API 연동이 아직 없다. 현재 `evidence_object_key`는 제출 문자열만 저장하며 객체 존재를 확인하지 않는다. 이 상태에서 심사자가 증빙을 검증할 수 없으므로 신규 관리자 승인·반려 버튼/API는 제공하지 않는다. 기존 회원 JWT 기반 심사 API는 별도 경로로 남아 있다.
- 감사 기록: `SUPER_ADMIN` 전용 검색. 관리자 ID·작업·대상 종류/ID·결과·기간 필터와 서버 페이지네이션. 응답에서 원본 JSON, IP, User-Agent를 제외한다.

## 측정 기준

| 항목 | 기준 및 결과 |
|---|---|
| OKR | 관리자 업무 변경을 BO에서 추적하고 수동 DB 조회를 0건으로 줄인다. |
| 목표 KPI | 감사 검색 비인가 허용 0건, 증빙 키 노출 0건, 4개 화면 폭 E2E 실패 0건. 목록 p95 ≤ 500ms는 운영 데이터 측정이 필요하다. |
| 평가셋 | MySQL 26.7 회귀 스크립트 전체와 신규 DB 통합 테스트 2건; 360/768/1024/1440px Playwright 28건. |
| Before → After | 기업·감사 화면 실제 API 연결 0/2 → 2/2; 신규 DB 평가셋에서 비인가 감사 조회 거부 0/1 → 1/1, 증빙 키 노출 0/1 유지. |
| 합격 기준 | DB 회귀 실패 0건, E2E 실패 0건, 비인가 감사 조회 403, 응답에 증빙 키·감사 원본 JSON·IP 미포함. |

## 검증 명령과 결과

- `./gradlew.bat compileJava --offline`: 성공.
- `bash ops/db/run-member-migration-tests.sh`: 성공. 대시보드 p95 21ms, 회원 목록 p95 5ms, 공고 목록 p95 5ms. 기업·감사 목록 p95는 이 평가셋에서 미측정.
- `npm.cmd run lint`, `npm.cmd run build`: 성공.
- `ADMIN_E2E_EXTERNAL_SERVER=true npm.cmd run e2e` (Vite 4175): 25 통과, 3 의도된 viewport 건너뜀, 실패 0.
- 커밋 `5168c3c`: 관리자 Workers, 백엔드, Docker CI 모두 성공.
- 운영 smoke: 관리자 `/login`, `/companies`, `/audit` 직접 경로 200; API health 200; 무인증 `/api/v1/admin/companies`, `/api/v1/admin/audit` 각각 401; 관리자 Origin에서 기업 API OPTIONS 200 및 허용 Origin 확인.

## 후속 작업

1. 증빙 업로드/저장소 소유권 검증과 관리자에게만 제공되는 만료형 열람 URL/API 구축.
2. 실제 증빙 확인 후 승인·반려, 관리자 심사자 FK, 상태 이력·감사 로그를 하나의 트랜잭션으로 구현.
3. 기업·감사 목록의 운영 크기 평가셋에서 p95 측정(합격 ≤ 500ms).
