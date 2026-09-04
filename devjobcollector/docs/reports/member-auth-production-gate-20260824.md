# DJC 회원 인증 G1 운영 배포 기록

## 상태

- 기준일: 2026-08-24 KST
- 배포 커밋: `4ebafac`
- CI 보정 커밋: `e2a540e`
- G1 상태: 완료(2026-09-04)
- 완료: 운영 배포, 자동 비파괴 smoke, Google/GitHub 성공 로그인, 동일 이메일 자동 병합 차단·명시적 연결, 24시간 관찰
- 후속: Career Hub 사람 수동 QA와 운영 전 인증 Secret 교체 여부 재검토

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
- 수동 OAuth 게이트: 합격(Google/GitHub 로그인 및 동일 이메일 충돌·명시적 연결)
- 24시간 관찰: 합격(Actions `33874026166`, 5xx 0/25·OAuth 처리 실패 0건)
- G1 최종 판정: 합격

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

## 수동 게이트 결과

브라우저 세션과 Provider 동의가 필요한 다음 검증을 운영자 확인으로 완료했다.

1. 기존 Google 계정 로그인 후 `<FRONTEND_DOMAIN>/oauth/callback` 성공 확인
2. 기존 GitHub 계정 로그인 후 동일 callback 성공 확인
3. 기존 회원과 동일한 이메일의 다른 Provider 로그인에서 `ACCOUNT_LINK_REQUIRED` 안내, 기존 계정 재인증 후 명시적 연결, 자동 연결 0건 확인

세 건 통과 후 관찰 필터 배포 시각인 2026-09-03 21:29:45 KST를 기준으로 rolling 24시간 인증 오류율을 관찰했다.

## 24시간 최종 관찰

- Actions: `33874026166`
- 평가 구간: 관찰 필터 배포 후 rolling 24시간
- read-only 운영 probe: 7/7
- 인증 요청: 5건, 5xx 0건
- Career 요청: 20건, 5xx 0건
- 전체: 25건, 5xx 0건(0.00%)
- OAuth 처리 실패: 0건
- 컨테이너 restart count: 0
- 원문 운영 로그 출력·업로드: 0건
- 목표 KPI: API 5xx `< 1%`, OAuth 처리 실패 0건, 원문 로그 외부 노출 0건을 모두 충족했다.
- Before/After: 1시간 baseline 10건·5xx 0건에서 24시간 25건·5xx 0건으로 평가 구간을 확대했다.
- 최종 판정: G1 합격. 신규 Provider 또는 기업 기능 착수 전 Career Hub 사람 수동 QA와 운영 전 인증 보안 재검토를 수행한다.

## OAuth callback HTTPS 보정

- 발견: GitHub authorization 요청의 `redirect_uri`가
  `http://<API_DOMAIN>/login/oauth2/code/github`로 생성되어 GitHub가 callback 불일치 경고를 표시했다.
- 원인: Cloudflare Tunnel 뒤의 애플리케이션이 forwarded HTTPS 스킴을 반영하지 않았다.
- 조치: 운영 환경에 `SERVER_FORWARD_HEADERS_STRATEGY=framework`를 명시하고,
  OAuth smoke가 Provider 도메인뿐 아니라 정확한 HTTPS callback까지 검사하도록 강화했다.
- 커밋: `ba92f83`
- 백엔드 배포: Actions `32728523788` 성공
- 독립 Docker CI: Actions `32728523844` 성공
- Before: Google/GitHub callback의 스킴이 `http://`
- After: 두 Provider 모두 `https://<API_DOMAIN>/login/oauth2/code/{provider}`
- 평가셋: health, 공개 검색, 기본 LOCAL 차단, Google/GitHub OAuth 진입·Provider 대상·HTTPS callback
- 결과: 자동 HTTP 검사 5/5 성공, Google/GitHub HTTPS callback 2/2 성공
- 실패 콜백 재현: GitHub `access_denied` 응답이
  `https://<FRONTEND_DOMAIN>/oauth/callback?error=OAUTH_LOGIN_FAILED`로 302 이동하고,
  프론트 callback 경로가 HTTP 200을 반환함을 확인했다.
- 잔여: 실제 Provider 계정 승인과 프론트 callback 성공 화면은 사용자 브라우저에서 수동 확인한다.
