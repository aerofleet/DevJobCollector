# DJC Company P6-01 보안 하드닝 검증 보고서

> 검증일: 2026-09-11
> 범위: 기업 가입·멤버 초대·기업 인증 요청/심사의 rate limit, 감사 이벤트, 운영 metrics 접근 제어

## 구현 결과

- 기업 가입, 멤버 초대, 기업 인증 요청, 플랫폼 관리자 심사의 4개 쓰기 경로에 고정 시간창 rate limit을 적용했다. 식별 차원은 SHA-256 digest로만 메모리에 보관한다.
- 제한 초과 응답은 HTTP 429와 `SECURITY_ACTION_RATE_LIMITED` 코드로 고정했다.
- 기업 생성, 멤버 초대·재초대, 역할 변경, 제거, 인증 요청·승인·반려의 7개 이벤트를 V7 `security_audit_events`에 기록한다.
- 감사 이벤트 저장은 호출 트랜잭션 참여를 강제하고, 감사 metric은 commit 이후에만 증가시켜 업무 변경과 감사 기록의 rollback 경계를 일치시켰다.
- metric tag는 action/event type과 outcome의 제한된 집합만 사용하며 사용자 ID, 사업자번호, 증빙 key를 포함하지 않는다.
- 운영 `/actuator/metrics/**`는 `PLATFORM_ADMIN`만 접근할 수 있다. health/info의 기존 공개 정책은 유지한다.
- `ops/db/audit-security-events.sql`로 기업별 감사 타임라인을 읽기 전용 조회할 수 있다.

## KPI / OKR

- **목표 KPI**: 보호 대상 쓰기 경로 4/4 rate limit 적용, 감사 이벤트 유형 7/7 영속화, rollback 시 업무·감사 불일치 0건, 감사 스키마의 비밀/개인정보 컬럼 0개, 전체 회귀 실패 0건.
- **OKR 연결**: 기업회원 MVP 운영 전 악의적·오작동성 반복 요청을 제한하고, 기업 권한 및 검증 상태 변경의 사후 추적 가능 범위를 100%로 만든다.
- **평가셋**: 단위·웹·MySQL 26.7 통합/마이그레이션 타깃 45건과 전체 Gradle 467건. rate limit 허용/거부/시간창 만료/원문 미보관, 7개 감사 유형, commit/rollback metric, V1 및 V6 upgrade, source 행 삭제 후 감사 보존, metrics 인증 경계를 포함한다.
- **Before/After**: 기업 쓰기 rate limit 적용 0/4 → 4/4, 전용 기업 보안 감사 이벤트 0/7 → 7/7, metrics 일반회원 차단 자동 검증 0건 → 2건(무토큰 401·일반회원 403), 전체 회귀 465건 → 467건이며 실패·오류는 계속 0건이다.
- **합격 기준**: 보안 타깃 45/45 및 전체 467/467 통과, failure/error/skip 0건, 제한 초과 HTTP 429 계약 일치, rollback 감사 row와 commit metric 증가 0건, 감사 테이블의 password/code/token/business number/evidence 컬럼 0개.

## 검증 명령과 결과

```powershell
$env:DJC_MIGRATION_TEST_URL='jdbc:mysql://127.0.0.1:33307/devjob_p601?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true'
$env:DJC_MIGRATION_TEST_USER='root'
$env:DJC_MIGRATION_TEST_PASSWORD='<TEMP_TEST_PASSWORD>'
.\gradlew.bat test --no-daemon
# BUILD SUCCESSFUL, 467 tests, failures=0, errors=0, skipped=0

git diff --check
# whitespace 오류 0건
```

## 운영 설정과 관찰 지표

- 시간창과 경로별 한도는 `SECURITY_RATE_LIMIT_*` 환경변수로 조정한다.
- 허용/거부 판단: `djc.security.rate_limit.decisions`
- commit 완료 감사 이벤트: `djc.security.audit.events`
- 추적 key 포화 거부: `djc.security.rate_limit.capacity`
- 모든 metric은 고정된 action/event/outcome tag만 사용한다.

## 남은 범위

- 현재 rate limiter는 JAR + systemd 단일 애플리케이션 프로세스 기준의 로컬 메모리 구현이다. 다중 인스턴스로 확장할 경우 분산 저장소 기반 원자 카운터를 별도 도입해야 한다.
- 운영 DB V7 적용과 실제 트래픽 기준 한도 조정은 배포 단계에서 수행한다. 이번 작업은 운영 배포를 포함하지 않는다.
- P6-02에서 동시성, 성능, V6→V7 migration rehearsal 및 rollback 절차를 수치로 검증한다.
