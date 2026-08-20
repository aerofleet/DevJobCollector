# DJC V3 Member Foundation Migration Validation

> 검증일: 2026-08-20 KST
> 작업 패키지: P1-01
> 운영 변경: 없음

## 결과

`V3__create_member_identity_and_consent.sql`에 `personal_profiles`, `user_consents`, `user_identities`와 V2 사용자 backfill을 구현했다. 운영 DB 실측 버전 `26.7.0-cloud`와 동일한 버전 계열의 공식 `mysql:26.7.0` 이미지에서 Flyway V1→V3 clean migration과 V2 fixture upgrade를 검증했다.

V3는 기존 `users` 인증 컬럼을 유지한다. 기존 약관 동의를 추정하지 않으며, 지원하지 않는 provider, 누락된 social subject, 중복 provider subject가 있으면 테이블 생성 전에 명시적인 migration 오류로 중단한다.

## 변경 범위

- V3 migration: profile/consent/identity DDL, 사전검증, legacy identity/profile backfill
- Flyway 통합 테스트: clean, 정상 V2 upgrade, LOCAL password 누락 허용, social subject 누락 실패, duplicate 실패, unsupported provider 실패
- CI: Backend deploy와 Docker validation 전에 MySQL 26.7 migration 평가셋 실행
- V1/V2 checksum 영향: 없음

## 평가셋과 결과

| 평가 항목 | 조건 | 기대 결과 | 실제 결과 |
|---|---|---|---|
| clean | 빈 MySQL 26.7.0, Flyway V1→V3 | 성공, history 포함 테이블 14개 | 통과 |
| V2 upgrade | LOCAL ACTIVE/PENDING, Google, GitHub 합성 사용자 | identity 4, ACTIVE profile 3 | 통과 |
| consent | legacy 사용자 4명 | consent 0건 | 통과 |
| LOCAL credential | password hash가 없는 LOCAL 1명 | migration 성공, 후속 audit 대상 | 통과 |
| missing subject | Google subject NULL | V3 실패 | 통과 |
| duplicate | GitHub 동일 subject 2명 | V3 실패 | 통과 |
| unsupported provider | V2에 KAKAO 1명 | V3 실패 | 통과 |
| integrity | 정상 upgrade 후 orphan/duplicate 집계 | 각 0건 | 통과 |
| regression | 전체 Gradle test + migration test | 오류 0건 | `BUILD SUCCESSFUL` |

테스트 결과 XML: migration test 6건, skipped 0, failures 0, errors 0.

## KPI / OKR / 합격 기준

- 목표 KPI: MySQL 26.7 clean/V2 upgrade 성공률 100%, identity orphan/duplicate 0건, 허위 consent backfill 0건, 실패 fixture 차단률 100%.
- OKR 연결: Multi-Provider 전환 전에 인증수단 1:N 기반과 감사 가능한 consent 저장 기반을 구축해 account takeover 위험을 낮춘다.
- 평가셋: Flyway integration 6건과 전체 Gradle regression 1회. 정상 V2 fixture 4명, 실패 fixture 4명.
- Before: V2의 `users.provider/provider_user_id` 단일 identity, profile/consent 테이블 없음, 운영 버전과 다른 MySQL 8.4 CI 기준.
- After: V3 identity/profile/consent 구조, 비정상 backfill 사전 차단, 운영과 동일한 26.7 계열 CI 기준.
- 합격 기준: integration 6/6, 전체 build 성공, V1/V2 변경 0개, 운영 DB mutation 0개.

## 검증 명령

```text
DJC_MIGRATION_TEST_EXPECTED_VERSION=26.7.0 ./gradlew test \
  --tests kr.itsdev.devjobcollector.migration.MemberV3MigrationTest --no-daemon

./gradlew clean test --no-daemon
git diff --check
```

실제 DB 주소, 계정, 비밀번호, OCID는 기록하지 않았다.

## 남은 위험과 다음 작업

- 공식 Community 이미지 `26.7.0`과 OCI Cloud build `26.7.0-cloud`는 같은 Server 26.7 계열이지만 배포 전 운영 schema-only rehearsal은 별도 게이트로 유지한다.
- P1-02에서 Entity/Repository를 구현하되 V3를 아직 운영 배포하지 않는다.
- P1-03에서 LOCAL password 누락 및 identity 품질 audit를 재사용 가능한 SQL로 고정한다.
