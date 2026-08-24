# DJC 회원 인증 G1 운영 배포 기록

## 상태

- 기준일: 2026-08-24 KST
- 배포 커밋: `4ebafac`
- CI 보정 커밋: `e2a540e`
- G1 상태: 진행 중
- 완료: 운영 배포, 자동 비파괴 smoke
- 미완료: Google/GitHub 성공 로그인 및 동일 이메일 충돌 수동 검증 3건, 이후 24시간 관찰

## 변경 범위

- Flyway V3 회원 identity/consent/profile 기반을 운영에 적용했다.
- LOCAL/Google/GitHub 인증의 source of truth를 `user_identities`로 전환했다.
- 동일 이메일 소셜 자동 병합을 `ACCOUNT_LINK_REQUIRED`로 차단했다.
- 이용약관과 개인정보 처리방침 버전 `2026-08-24`를 운영 환경에 명시했다.
- 프론트엔드 `/terms`, `/privacy` 페이지와 회원가입 원문 링크를 배포했다.
- 운영자와 자격증명 값, 실제 IP·도메인·OCID는 문서에 기록하지 않는다.

## 배포 결과

### 첫 시도와 롤백

- Actions: `32720845156`, attempt 1
- 전체 백엔드 테스트와 MySQL 26.7 평가셋은 성공했다.
- 신규 컨테이너는 DB `Access denied`로 health 게이트를 통과하지 못했다.
- 배포 스크립트가 이전 이미지와 환경파일을 자동 복구했다.
- 롤백 후 `<API_DOMAIN>/actuator/health`와 공개 검색 API는 HTTP 200을 유지했다.
- 원인: GitHub `DB_PASSWORD` Secret과 운영 `djc_app` 자격증명이 일치하지 않았다.

### 재배포

- Actions: `32720845156`, attempt 2
- 결과: 성공
- 전체 Gradle 테스트: `BUILD SUCCESSFUL`
- MySQL 26.7 clean/V2 upgrade 평가: `BUILD SUCCESSFUL`
- 이미지: `linux/amd64`, `linux/arm64` 빌드 및 GHCR push 성공
- 필수 배포 Secrets 검증: 성공
- 운영 컨테이너 health: attempt `8/120`에서 통과
- 최종 상태: `DJC is running with Docker Compose`

### 독립 Docker CI

- 첫 실행 `32720845188`: `gradlew` 실행 권한 누락으로 실패
- 수정: 마이그레이션 테스트 전에 `chmod +x gradlew` 추가
- 재실행 `32722787257`: 성공
- 애플리케이션 또는 운영 데이터 변경 없이 CI 실행 환경만 보정했다.

### 프론트엔드

- Actions: `32720845025`
- Cloudflare Workers 배포 성공
- `<FRONTEND_DOMAIN>/terms`: HTTP 200
- `<FRONTEND_DOMAIN>/privacy`: HTTP 200

## KPI / OKR

### 목표 KPI

- 배포 테스트 및 MySQL 26.7 평가 성공률: 100%
- 신규 컨테이너 health 게이트: 120회 이내 `UP`
- 자동 인증 smoke 성공률: 100%
- 기존 기본 LOCAL 자격증명 허용: 0건
- 자동 이메일 병합 허용: 0건
- 실패 배포 시 기존 서비스 복구율: 100%

### OKR 연결

- 기존 회원 로그인 경로를 보존하면서 Account Takeover 위험이 있는 이메일 자동 병합을 제거한다.
- 회원 동의 버전 감사 누락률 0%를 유지해 기업회원 MVP 전 개인회원 기반을 확정한다.

### 평가셋

- GitHub Actions 전체 Gradle 테스트 1회
- MySQL 26.7 clean install 및 V2 snapshot upgrade 1회
- `linux/amd64`, `linux/arm64` 이미지 빌드 1회
- 운영 컨테이너 health 최대 120회
- 비파괴 HTTP smoke 5건
  - actuator health
  - 공개 채용 검색
  - 과거 기본 LOCAL 자격증명 거부
  - Google OAuth 진입 및 redirect 대상
  - GitHub OAuth 진입 및 redirect 대상

### Before / After

| 항목 | Before | After |
| --- | --- | --- |
| 운영 회원 스키마 | Flyway V2 | Flyway V3 identity/consent/profile |
| 정책 버전 운영값 | 애플리케이션 기본값 `v1` 가능 | GitHub Secrets `2026-08-24` 필수 검증 |
| GitHub OAuth | 운영 Secret 없음 | 전용 client ID/secret 등록 및 OAuth 진입 HTTP 302 |
| 첫 배포 DB 인증 | 신규 컨테이너 `Access denied` | Secret 갱신 후 health attempt 8/120 통과 |
| 자동 smoke | 미실행 | 5/5 성공, 오류율 0% |

### 현재 합격 여부

- 자동 게이트: 합격
- 수동 OAuth 게이트: 미평가
- 24시간 관찰: 미시작
- G1 최종 판정: 보류

## 자동 smoke 결과

```text
PASS actuator health: HTTP 200
PASS public job search: HTTP 200
PASS former default LOCAL credential rejection: HTTP 401
PASS google OAuth entry: HTTP 302
PASS google OAuth redirect target
PASS github OAuth entry: HTTP 302
PASS github OAuth redirect target
SKIP active LOCAL login: smoke credentials were not supplied
PASS member auth non-destructive smoke: 5 HTTP checks
```

## 잔여 게이트

브라우저 세션과 Provider 동의가 필요한 다음 검증은 운영자가 직접 수행한다.

1. 기존 Google 계정 로그인 후 `<FRONTEND_DOMAIN>/oauth/callback` 성공 확인
2. 기존 GitHub 계정 로그인 후 동일 callback 성공 확인
3. 기존 회원과 동일한 이메일의 다른 Provider 로그인에서 `ACCOUNT_LINK_REQUIRED` 안내 및 자동 연결 0건 확인

세 건을 모두 통과한 시각부터 24시간 인증 오류율을 관찰한다. 수동 검증과 관찰이 끝나기 전에는 G1을 완료 처리하거나 신규 Provider 구현을 시작하지 않는다.
