# Member V3 backfill audit 검증 보고서

- 작업: P1-03 legacy identity backfill 품질 및 duplicate/orphan audit
- 검증일: 2026-08-20 KST
- 대상 DB: MySQL 26.7.0 (운영 런타임 `26.7.0-cloud`와 동일 계열)
- 운영 DB 주소: `<DB_PRIVATE_IP>:3306`
- 배포: 미수행

## 결과

`ops/db/audit-member-v3.sql`을 추가했다. 단일 read-only 쿼리가 사용자·identity·profile·consent 기준 수량과 18개 결함 지표를 `metric/value` 형식으로 반환한다. 이메일, provider subject, password hash, token 같은 행 단위 식별·인증 정보는 출력하지 않는다.

감사 지표는 다음 cutover 차단 조건을 포함한다.

- legacy: 지원하지 않는 provider, LOCAL password 누락, social subject 누락
- identity: 사용자별 누락, orphan, provider+subject 중복 초과, user+provider 중복 초과, legacy 원본 불일치, 허용되지 않은 provider
- profile: orphan, ACTIVE 사용자 누락, 비ACTIVE 사용자 보유, 잘못된 status
- consent: orphan, 잘못된 type/action, 빈 policy version

## 검증 중 발견 및 수정

첫 실제 실행에서 NULL social subject 비교가 SQL 3값 논리 때문에 mismatch 집계에서 빠졌다. 일반 부등호 대신 MySQL NULL-safe equality(`<=>`)의 부정을 사용해 누락 subject도 mismatch로 집계하도록 수정했다.

duplicate fixture에서는 `(user_id, provider)` unique index가 FK 보조 인덱스로 사용되어 곧바로 제거되지 않았다. 별도 `user_id` 인덱스를 먼저 추가한 뒤 unique index drift를 재현해 두 중복 지표가 각각 초과 행 1건을 검출하는지 검증했다.

## KPI / OKR / 평가셋

- 목표 KPI: 정상 fixture 결함 지표 0건, 주입한 결함 검출률 100%, MySQL 26.7 관련 테스트 성공률 100%, 민감 원문 출력 0건.
- OKR 연결: P1 V3 cutover 전에 identity backfill 무결성을 수치로 증명하고 P2 인증 전환의 account takeover 및 계정 고립 위험을 차단한다.
- 평가셋: 정상 backfill 1종, legacy credential/subject/provider 결함 1종, identity 누락·불일치 1종, constraint drift duplicate 1종, identity/profile/consent orphan·enum 결함 1종. 기존 migration 6건과 repository 5건을 함께 실행했다.
- Before: V3 migration 구조 검증은 있었지만 운영자가 반복 실행할 aggregate cutover audit와 결함 주입 평가셋이 없었다.
- After: 22개 집계 metric과 5개 audit fixture가 생겼고 MySQL 26.7에서 관련 테스트 16/16이 실제 실행되어 통과했다.
- 합격 기준: 정상 fixture 모든 결함 지표 0, 주입 결함별 기대 count 일치, 관련 테스트 skip/failure/error 0, 전체 Gradle 회귀 성공, 행 단위 민감값 출력 0.

## 검증 명령 및 결과

```text
set DJC_MIGRATION_TEST_URL=jdbc:mysql://127.0.0.1:<LOCAL_TEST_PORT>/devjob
set DJC_MIGRATION_TEST_USERNAME=<TEST_USER>
set DJC_MIGRATION_TEST_PASSWORD=<TEST_PASSWORD>
set DJC_MIGRATION_TEST_EXPECTED_VERSION=26.7.0
gradlew.bat cleanTest test \
  --tests kr.itsdev.devjobcollector.migration.MemberV3MigrationTest \
  --tests kr.itsdev.devjobcollector.migration.MemberV3AuditTest \
  --tests kr.itsdev.devjobcollector.security.account.MemberFoundationRepositoryTest \
  --no-daemon
```

- 결과: `BUILD SUCCESSFUL`
- `MemberV3AuditTest`: 5 tests, skipped 0, failures 0, errors 0
- `MemberV3MigrationTest`: 6 tests, skipped 0, failures 0, errors 0
- `MemberFoundationRepositoryTest`: 5 tests, skipped 0, failures 0, errors 0
- 합계: 16/16 통과

```text
gradlew.bat clean test --no-daemon
```

- 결과: `BUILD SUCCESSFUL in 40s`

```text
git diff --check
docker run --rm --volume <PROJECT_ROOT>:/work mysql:26.7.0 \
  bash -n /work/ops/db/run-member-migration-tests.sh
```

- 결과: 오류 없음

## 변경 불변조건 및 잔여 위험

- Flyway V1/V2/V3 파일은 수정하지 않았다.
- 실제 운영 DB에는 audit를 실행하거나 schema/data를 변경하지 않았다.
- CI/test MySQL 26.7에 대해 Flyway가 공식 지원 상한보다 최신이라는 경고는 계속 존재한다. 이번 평가셋은 현재 사용 SQL의 호환성을 실측하지만 Flyway 공급자의 공식 지원을 대신하지 않는다.
- 다음 P2-01에서는 가입 request의 필수 동의를 `user_consents` append-only 행으로 연결하고, 이후 LOCAL/Google/GitHub 인증 경로를 identity 기준으로 전환한다.
