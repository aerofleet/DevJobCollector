# DJC 관리자 P2 대시보드 검증 보고서

## 범위

- 관리자 세션 전용 `GET /api/v1/admin/dashboard/summary`
- 전체 일반 회원, 금주 신규 가입, 기업 인증 검토 대기, 활성·노출·미마감 공고 집계
- Asia/Seoul 기준 최근 7일 신규 가입 추이와 빈 날짜 0건 보정
- 관리자 SPA 지표 카드, 7일 막대 추이, 기업 심사 대기 표시

## KPI / OKR / 평가셋

- **OKR 연결**: 운영자가 회원·기업·공고 현황을 단일 BO에서 확인하게 하여 운영 DB 수동 조회를 0건으로 만든다.
- **목표 KPI**: 대시보드 요약 API p95 `< 800ms`, 일반 회원 JWT 관리자 대시보드 접근 성공 0건, 집계·UI 회귀 실패 0건.
- **평가셋**:
  - 서비스 집계 경계와 7일 zero-fill 1건
  - 관리자 전용 세션 허용·일반 회원 JWT 차단 2건
  - MySQL 26.7 실제 V1~V8 스키마에서 정상·숨김·만료 공고와 회원·기업 fixture 집계 1건
  - warm-up 3회 후 요약 조회 30회 p95 측정
  - 관리자 SPA 360/768/1024/1440px 9건, 모바일 전용 3건은 비대상 viewport에서 의도적 skip
- **합격 기준**: p95 `< 800ms`, 네 지표와 7일 추이 오집계 0건, 무관리자 세션 응답 401, Gradle·lint·build·E2E 실패 0건.

## Before / After

| 항목 | Before | After |
|---|---:|---:|
| 대시보드 요약 API | 미구현 | MySQL 26.7 p95 `15ms` |
| 운영 지표 | 프론트 계약만 존재 | 4개 지표 실데이터 연결 |
| 가입 추이 | 미구현 | 최근 7일 일별 집계·빈 날짜 0건 보정 |
| 기업 심사 대기 | 정적 안내 | PENDING 요청 실시간 건수 |
| 일반 회원 JWT 접근 | 전용 경로 미구현 | 성공 `0/1`, HTTP 401 |

## 결과

| 평가 | 결과 |
|---|---:|
| 로컬 전체 Gradle | 436 passed / 103 환경 조건부 skip / failures 0 / errors 0 |
| MySQL 26.7 통합 | 기존 게이트 + 신규 대시보드 평가 통과, 신규 skip 0 |
| 대시보드 성능 | 30회 p95 `15ms` / 목표 대비 98.1% 여유 |
| 관리자 SPA lint/build | 통과 / production build 성공 |
| 4 viewport E2E | 9 passed / 3 의도적 skip |
| Backend/Docker/Admin 배포 | 3/3 성공 |

## 운영 확인

- 백엔드 배포 워크플로우의 컨테이너 내부 health gate 통과
- 관리자 SPA Cloudflare Workers 배포 성공
- 실제 대시보드 응답은 관리자 HttpOnly 세션이 필요하므로 무인증 외부 smoke는 401을 합격 기준으로 사용한다.

## 다음 작업

- P2 회원 목록·상세·정지/해제와 관리자 조치 시 회원 세션 폐기
- 상태 변경의 `expectedVersion`, idempotency key, 감사 로그 원자성 평가
