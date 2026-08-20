# DJC V3 Member Foundation Domain Validation

> 검증일: 2026-08-20 KST
> 작업 패키지: P1-02
> 운영 변경: 없음

## 결과

V3 스키마와 일치하는 `UserIdentity`, `UserConsent`, `PersonalProfile` 엔티티와 Spring Data JPA Repository를 구현했다. 운영 DB와 동일한 버전 계열인 MySQL 26.7.0에서 Hibernate schema validation, 실제 insert/query, binary subject 비교, unique/FK 제약을 검증했다.

`UserConsent`에는 상태 변경 메서드를 제공하지 않고 ACCEPTED/REVOKED를 별도 행으로 저장한다. `UserIdentity.providerSubject`와 issuer는 생성 후 변경할 수 없으며 credential secret 필드는 포함하지 않는다.

## 변경 범위

- `AuthProvider`: LOCAL/GOOGLE/GITHUB에 KAKAO/NAVER/APPLE 예약값 추가
- `UserAccountStatus`: SUSPENDED/WITHDRAWN 상태 추가
- Identity: provider+subject 및 user+provider 조회, provider email/last login metadata
- Consent: append-only event와 timeline/latest query
- Profile: user 공유 PK, ACTIVE/PRIVATE/DELETED 상태
- CI migration runner: Repository integration test 포함
- Flyway V1/V2/V3 변경: 없음

## 평가셋과 결과

| 평가 항목 | 조건 | 합격 기준 | 결과 |
|---|---|---|---|
| Domain | identity/consent/profile factory와 상태 | 5/5 성공 | 통과 |
| Schema | Hibernate `ddl-auto=validate`, MySQL 26.7.0 | mismatch 0 | 통과 |
| Identity lookup | binary subject 대소문자 비교 | exact match만 조회 | 통과 |
| Provider subject unique | 다른 사용자, 같은 provider+subject | DB 차단 | 통과 |
| User provider unique | 같은 사용자, 같은 provider 2건 | DB 차단 | 통과 |
| Consent timeline | ACCEPTED 후 REVOKED | 행 2개, latest REVOKED | 통과 |
| Profile PK | user_id 공유 PK | user/profile ID 일치 | 통과 |
| Secret field | identity reflection scan | password/secret/token 필드 0 | 통과 |
| Regression | migration 6 + repository 5 + domain 5 | failure/error/skip 0 | 16/16 통과 |

전체 명령 `gradlew clean test --no-daemon`은 `BUILD SUCCESSFUL`, 10 actionable tasks 실행으로 종료됐다.

## KPI / OKR / 합격 기준

- 목표 KPI: 관련 평가셋 성공률 100%, schema mismatch 0건, duplicate 저장 성공 0건, append-only event 누락 0건, identity secret 필드 0개.
- OKR 연결: P2 인증 읽기/쓰기 전환 전에 provider identity와 consent/profile 영속성 계약을 코드로 고정해 계정 탈취와 감사 이력 손상 위험을 낮춘다.
- 평가셋: MySQL 26.7 Flyway 6건, Repository 5건, 도메인 5건 및 전체 Gradle 회귀 1회.
- Before: V3 물리 테이블만 존재하고 매핑 엔티티/Repository 및 DB 제약 통합 테스트 없음.
- After: 세 엔티티·세 Repository, 상태/타입 enum, MySQL 26.7 schema/constraint 자동 검증.
- 합격 기준: 관련 테스트 16/16, 전체 build 성공, Flyway migration 변경 0개, 운영 DB mutation 0개.

## 남은 위험과 다음 작업

- P1-02는 영속성 기반만 추가하며 기존 LOCAL/Google/GitHub 인증 읽기 경로는 아직 V2 컬럼을 사용한다.
- P1-03에서 legacy LOCAL password 누락, provider subject 품질, duplicate/orphan audit SQL을 재사용 가능하게 고정한다.
- P2에서 가입/로그인 서비스가 새 Repository를 사용하도록 트랜잭션 경계를 전환한다.
