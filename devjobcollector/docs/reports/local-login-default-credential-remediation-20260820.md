# DJC 기본 LOCAL credential 보안 조치 — 2026-08-20

## 판정

P0-SEC-01을 완료했다. 설정 기반 LOCAL fallback은 기본 비활성화됐고 기본 username/email/password 값은 제거됐다. DB에 저장된 LOCAL 회원 인증 경로는 유지된다.

- 보안 수정 커밋: `3455605`
- 배포 전 기본 credential 로그인: HTTP 200
- 배포 후 동일 로그인: HTTP 401
- 최종 배포: GitHub Actions `32281677907` 성공
- 외부 API health: HTTP 200
- 외부 검색 API: HTTP 200
- signup 페이지: HTTP 200

실제 credential 값과 운영 도메인은 외부 공유 문서에서 각각 `<REMOVED_DEFAULT_CREDENTIAL>`, `<API_DOMAIN>`으로 마스킹한다.

## 변경

- `AuthLocalLoginProperties.enabled` 기본값을 `false`로 변경.
- `application.yml`의 fallback 기본 계정과 비밀번호 제거, users 기본값을 빈 목록으로 변경.
- 운영 배포 bundle에 `AUTH_LOCAL_LOGIN_ENABLED=false`를 명시해 defense in depth 적용.
- 기존 기본 credential 거부, DB ACTIVE LOCAL 로그인 유지, 명시적 opt-in 동작을 검증하는 단위 테스트 4건 추가.

## 배포 중 발견한 문제

첫 배포 run `32281038154`는 현재 `djc-app-1` 컨테이너가 정상 점유한 TCP 8080을 배포 스크립트가 알 수 없는 충돌로 오판해 실패했다. 스크립트가 자동 rollback하여 기존 컨테이너와 health는 유지됐다.

커밋 `ae4bba8`에서 현재 Compose project/service label과 image repository를 검증한 경우 기존 컨테이너를 Compose가 교체하도록 수정했다. 알 수 없는 프로세스 또는 예상 밖 image가 포트를 점유하면 기존처럼 배포를 중단하고 rollback한다.

## 검증

- 집중 테스트: `gradlew test --tests "kr.itsdev.devjobcollector.security.service.LocalCredentialAuthServiceTest" --no-daemon` → 성공, 4건.
- 전체 테스트: `gradlew clean test --no-daemon` → `BUILD SUCCESSFUL`, 10 tasks.
- 배포 스크립트: `bash -n ops/docker/deploy.sh` → 성공.
- 기본 credential 문자열 scan → 0건.
- 최종 Actions: test, multi-architecture image, Tunnel SSH, container health 모두 성공.
- 운영 smoke: 기본 credential 401, health/search/signup 모두 200.

## KPI / OKR / 평가셋

- 목표 KPI: 알려진 기본 credential 로그인 성공률 0%, 정상 health/search/signup 성공률 100%, 배포 rollback 성공률 100%.
- OKR 연결: V3 identity 전환 전에 기존 인증 우회경로를 제거해 account takeover 가능성을 차단한다.
- 평가셋: fallback unit 4건, 전체 backend test 1회, Bash syntax 1회, 문자열 scan 1회, Actions 배포 2회, 운영 endpoint 4종.
- Before: 설정 fallback 기본 활성, 기본 credential 로그인 HTTP 200.
- After: 기본 비활성·기본값 제거·운영 env 강제 false, 동일 로그인 HTTP 401.
- 합격 기준: default credential 401, DB 로그인 단위 테스트 통과, health/search/signup 200 — 모두 충족.

## 잔여 위험과 다음 작업

- 명시적으로 `AUTH_LOCAL_LOGIN_ENABLED=true`와 users 목록을 주입하면 fallback은 여전히 opt-in 가능하다. 일반 회원 identity로 사용하지 않으며, 후속 hardening에서 별도 break-glass 경로 또는 코드 제거를 결정한다.
- Actions의 `setup-java@v4`와 일부 action이 deprecation 경고를 출력한다. 현재 실패 원인은 아니며 별도 CI maintenance 작업으로 이관한다.
- 다음은 P1-01: V3 DDL과 clean/V2 upgrade migration test 구현이다.
