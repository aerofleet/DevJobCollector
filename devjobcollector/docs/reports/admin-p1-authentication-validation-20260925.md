# DJC 관리자 P1 인증 기반 검증 보고서

## 범위

- 관리자 인증 경로 전용 `SecurityFilterChain`과 일반 회원 JWT 경계 분리
- 이메일·비밀번호·TOTP 로그인, 5회 실패 시 30분 잠금
- 해시 저장형 8시간 서버 세션과 HttpOnly/Secure/SameSite 쿠키
- Origin allowlist, CSRF 이중 제출 토큰, `X-Request-Id`, `Cache-Control: no-store`
- `/api/v1/admin/auth/login`, `/auth/logout`, `/me`
- 최초 SUPER_ADMIN TOTP seed AES-256-GCM 암호화

## KPI / OKR / 평가셋

- **OKR 연결**: 일반 회원 인증과 분리되고 감사 가능한 관리자 접근 경로를 제공해 운영 DB 수동 조작을 0건으로 만든다.
- **목표 KPI**: 일반 회원 JWT 관리자 인증 성공 0건, 변조 CSRF 허용 0건, TOTP 없는 bootstrap 허용 0건, 인증 관련 회귀 실패 0건.
- **평가셋**:
  - 관리자 도메인·bootstrap·암호화·TOTP·Origin·CSRF·세션 격리 단위 평가 27건
  - 전체 Gradle 534건
  - MySQL 26.7 V1~V8 및 저장소 통합 102건
- **Before**: `/api/v1/admin/**`이 일반 회원 JWT 인증을 공유했고 관리자 로그인 API와 MFA 검증이 없었다.
- **After**: 관리자 인증 경로에 쿠키 세션 전용 보안 체인, TOTP, 잠금, CSRF/Origin 검증과 인증 API 3개가 추가됐다. 기존 기업 심사 API는 P2 전환 전까지 일반 회원 `PLATFORM_ADMIN` 호환 경로를 유지한다.
- **합격 기준**: 단위·전체·MySQL 평가셋 실패/오류 0건, 일반 회원 Authorization 헤더만으로 관리자 principal 생성 0건.

## 결과

| 평가 | 결과 |
|---|---:|
| 관리자 단위 평가 | 27/27 통과 |
| 전체 Gradle | 432 passed / 102 환경 조건부 skip / failures 0 / errors 0 |
| MySQL 26.7 통합 | 102/102 통과, skip 0 |
| 일반 회원 JWT 격리 | 1/1 통과 |
| Origin·CSRF 거부/허용 | 3/3 통과 |

## 운영 설정

- 지속 비밀값: `ADMIN_MFA_ENCRYPTION_KEY` (base64 인코딩된 32바이트 키)
- 운영 Origin: `ADMIN_ALLOWED_ORIGINS=https://djc-admin.itsdev.kr`
- CSRF 공유 쿠키 Domain: `ADMIN_COOKIE_DOMAIN=.itsdev.kr`
- bootstrap 시에만 `ADMIN_BOOTSTRAP_MFA_SECRET`을 주입하고 완료 직후 제거한다.

## 남은 운영 게이트

초기 SUPER_ADMIN 실계정 프로비저닝과 실제 TOTP 앱 로그인은 운영 비밀번호와 TOTP seed 전달이 필요한 별도 승인 작업이다. P2 업무 API 착수 전 실계정으로 로그인·`/me`·로그아웃 3개 경로를 검증한다.
