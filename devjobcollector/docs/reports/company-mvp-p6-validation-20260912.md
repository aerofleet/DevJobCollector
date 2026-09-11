# DJC Company P6-02 동시성·성능·마이그레이션 검증 보고서

> 검증일: 2026-09-12
> 범위: 기업 가입·초대·인증 요청 동시성, 기업 가입 DB 구간 성능, V6→V7 migration, V6-era 애플리케이션 rollback

## 구현 및 결함 조치

- 동일 입력 20회, concurrency 10을 재현하는 MySQL 통합 평가셋을 추가했다.
- 최초 평가에서 동일 기업 인증 요청이 5건 성공해 PENDING 중복 경쟁 조건을 검출했다.
- 원인은 MySQL REPEATABLE READ에서 기업 행 잠금 전에 만들어진 transaction snapshot을 일반 `exists` 조회가 재사용한 것이다.
- pending 요청 조회를 `PESSIMISTIC_WRITE` current read로 바꿔 기업 행 잠금 뒤 최신 commit을 읽도록 보정했다.
- DB 통합 실행 스크립트에 V7 migration, 기업 P6 동시성, 보안 하드닝 테스트를 포함했다.

## KPI / OKR

- **목표 KPI**: 동일 사업자번호·동일 초대 대상·동일 인증 요청 각각 최종 업무 row 1건, 감사 이벤트 1건, 기업 가입 DB 구간 p95 300ms 이하, 오류율 0%, clean V1→V7 및 V6→V7 100%, 이전 애플리케이션 rollback 1/1.
- **OKR 연결**: 기업회원 MVP에서 중복 기업·membership·검증 요청을 0건으로 유지하고, 배포 및 롤백 시 회원·기업 데이터와 감사 이력을 보존한다.
- **평가셋 정의**: MySQL Enterprise 26.7.0 이미지, 요청별 20회/concurrency 10, 고유 기업 가입 warm-up 3건+측정 30건, 전체 Gradle 471건, V1→V7 clean migration, V6 fixture 보존 upgrade, V7 DB에 V6-era 커밋 `c3072d5` 연결.
- **Before/After**: 동시 인증 요청 성공 5/20·PENDING 5건 → 성공 1/20·PENDING 1건, 기업 가입 성능 기준 미측정 → p50 18ms/p95 21ms, rollback 호환 미검증 → 이전 커밋 기업 Repository 5/5 통과.
- **합격 기준**: 동시성 3종 각각 성공 1건과 최종 row·감사 1건, 가입 p95 ≤ 300ms와 errors=0, Flyway 성공 이력 V1~V7 7/7, 전체 회귀 failures/errors/skipped 0, rollback 후 V7 schema 보존.

## 측정 결과

| 평가 | 결과 | 판정 |
|---|---:|---|
| 동일 사업자번호 가입 20회 / concurrency 10 | 성공 1, 중복 4, rate limited 15, 기업·OWNER·감사 각 1 | 합격 |
| 동일 이메일 초대 20회 / concurrency 10 | 성공 1, 중복 19, membership·감사 각 1 | 합격 |
| 동일 인증 요청 20회 / concurrency 10 | 성공 1, pending 중복 거부 4, rate limited 15, PENDING·감사 각 1 | 합격 |
| 고유 기업 가입 30건 | p50 18ms, p95 21ms, errors 0 | 합격 |
| V1→V7 clean migration | V7, 22 tables, 감사 테이블 1 | 합격 |
| V6→V7 migration | 기존 기업 1건 보존, 신규 감사 row 0 | 합격 |
| V6-era 앱 → V7 DB rollback | 기업 Repository 5/5, V1~V7 이력 7/7 보존 | 합격 |
| 전체 Gradle | 471/471, failures/errors/skipped 0 | 합격 |

가입 p95는 목표 300ms 대비 279ms 낮고 목표의 7.0% 수준이다. 로컬 단일 클라이언트 DB 구간 측정이므로 운영 네트워크 end-to-end latency를 대체하지 않는다.

## 검증 명령과 결과

```powershell
$env:DJC_MIGRATION_TEST_URL='jdbc:mysql://127.0.0.1:<TEST_PORT>/devjob_p602?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true'
$env:DJC_MIGRATION_TEST_USERNAME='root'
$env:DJC_MIGRATION_TEST_PASSWORD='<TEMP_TEST_PASSWORD>'
$env:DJC_MIGRATION_TEST_EXPECTED_VERSION='26.7.0'
.\gradlew.bat test --no-daemon
# BUILD SUCCESSFUL, 471 tests, failures=0, errors=0, skipped=0

bash -n ops/db/run-member-migration-tests.sh
# exit 0

git diff --check
# whitespace 오류 0건
```

rollback 상세 절차는 `ops/db/company-v7-rollback-rehearsal.md`를 따른다. 운영 배포 시에는 현재 JAR + systemd 파이프라인에서 직전 정상 JAR을 사용하고 V7 테이블은 보존한다.

## 남은 범위

- rate limiter는 현재 단일 JAR 프로세스 로컬 메모리 기준이다. 다중 인스턴스 전환 시 분산 카운터 동시성 평가를 별도로 수행한다.
- 운영 네트워크를 포함한 p95는 배포 후 synthetic 또는 실제 트래픽 지표로 별도 측정한다.
- 다음 구현 단계는 P7-01 Provider framework/state registry 정리다.
