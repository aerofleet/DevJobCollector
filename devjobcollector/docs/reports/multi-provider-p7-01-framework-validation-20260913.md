# Multi-Provider P7-01 Framework / State Registry 검증

## 1. 범위

- 작업: P7-01 Provider framework/state registry
- 구현일: 2026-09-13
- 포함:
  - Google/GitHub 프로필 변환을 `OAuth2ProfileAdapter`로 분리
  - adapter 중복을 차단하는 immutable `OAuthProviderRegistry`
  - Kakao/Naver/Apple registration ID 예약
  - TTL, 최대 pending 수, 병렬 탭, 일회성 소비를 지원하는 세션 OAuth state registry
  - Spring Security authorization endpoint와 state registry 연결
- 제외:
  - Kakao/Naver/Apple 실제 callback 파싱 및 외부 자격증명
  - OIDC nonce/ID Token 검증과 Provider별 PKCE
  - 신규 Provider account link/unlink

## 2. KPI와 OKR

| 항목 | 목표 KPI | 결과 | 판정 |
|---|---:|---:|---|
| 기존 활성 Provider adapter | Google/GitHub 2/2 | 2/2 | 통과 |
| 확장 대상 registration ID | 5개 Provider 100% 매핑 | 5/5 | 통과 |
| state 보안 평가셋 | 4/4 | 4/4 | 통과 |
| auth-common 회귀 | 오류율 0% | 12/12, failures/errors/skipped 0 | 통과 |
| 전체 Gradle build | 성공 | 성공, 478건 중 391 passed·87 환경 조건 skip | 통과 |
| secret/token 로그 노출 | 0건 | 0건 | 통과 |

- OKR 연결: 2026-10-30 Multi-Provider 인증 완료 목표에서 Provider 추가 비용을
  adapter 단위로 제한하고, OAuth callback CSRF 방어를 모든 Provider에 공통 적용한다.

## 3. 평가셋

### Provider registry

1. Google/GitHub adapter 2개만 활성 상태로 조회된다.
2. Kakao/Naver/Apple을 포함한 registration ID 5개가 enum에 정확히 매핑된다.
3. adapter가 없는 예약 Provider 요청은 거부된다.
4. 동일 Provider adapter 중복 등록은 거부된다.

### OAuth state

1. Google/GitHub 병렬 시작 state를 같은 세션에서 독립 보존한다.
2. 위조 state는 조회되지 않는다.
3. TTL 5분에 도달한 state는 만료된다.
4. 최대 pending 수 초과 시 가장 오래된 state를 제거한다.
5. callback 제거 후 같은 state를 다시 사용할 수 없다.
6. state 없는 authorization request는 저장하지 않는다.

조건: Java 21, Spring Boot 3.5.11, in-memory mock HTTP session. 외부 Provider와
MySQL 연결은 이 평가셋에 필요하지 않다.

## 4. Before / After

| 구분 | Before | After |
|---|---|---|
| 프로필 변환 | Google/GitHub switch 문 | Provider별 adapter + immutable registry |
| Provider 확장 | 공통 switch와 enum 직접 수정 | adapter Bean 추가 후 자동 등록 |
| 예약 Provider | DB enum에만 존재 | auth-common registration ID 5개 매핑 |
| state 저장 | Spring 기본 단일 session authorization request | state별 registry, TTL 5분, 최대 8개 |
| 병렬 탭 | 후속 요청이 기존 요청을 덮을 수 있음 | state별로 독립 보존 |
| replay 제한 | callback filter 기본 제거에 의존 | registry에서 callback state 일회성 제거 |

## 5. 검증 명령과 결과

```powershell
.\gradlew.bat :auth-common:test --tests "kr.itsdev.auth.common.oauth.*"
.\gradlew.bat test
git diff --check
```

- auth-common: 12/12, failures/errors/skipped 0
- 전체 Gradle: BUILD SUCCESSFUL, 478건 중 391 passed, 87 skipped
- skip 87건은 MySQL/Testcontainers 실행 조건이 없는 경우 비활성화되는 기존 통합 평가셋이다.
- whitespace 오류: 0건

## 6. 운영 반영

- 구현 커밋: `db9b837`
- Backend Actions: `34761982757` 성공
- Docker Actions: `34761982793` 성공
- 운영 비파괴 smoke:
  - health 200
  - 공개 공고 검색 200
  - 무토큰 회원 API 401
  - Google OAuth 시작 302
  - GitHub OAuth 시작 302

## 7. 합격 기준 및 제한

합격 기준은 활성 adapter 2/2, 예약 ID 5/5, state 평가셋 4/4, auth-common 오류율
0%, secret/token 로그 노출 0건이다. 모든 기준을 충족했다.

세션 state registry는 현재 JAR + systemd 단일 인스턴스 배포 기준이다. 다중 인스턴스로
확장할 경우 sticky session 또는 공유 세션 저장소를 먼저 적용해야 한다. Kakao OIDC의
nonce와 ID Token signature/issuer/audience/expiration 검증은 P7-02에서 별도 평가한다.
