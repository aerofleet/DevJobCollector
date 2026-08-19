# DJC 회원 기반 운영 DB 감사 및 V2 스냅샷 지침

## 목적과 범위

V3 `user_identities`, `personal_profiles`, `user_consents` 도입 전에 운영 DB가 Flyway V2 계약과 일치하는지 검증한다. 운영 데이터는 변경하지 않으며 이메일, 이름, provider subject, password hash, 인증 코드 등 식별 가능한 값은 출력하거나 저장하지 않는다.

실행 SQL은 `ops/db/audit-member-foundation.sql`이다. SQL은 `SELECT`와 `information_schema` 조회만 사용하며 집계값과 스키마 메타데이터만 출력한다.

## 산출물

- `member-foundation-audit.txt`: Flyway 이력, provider/status/role 분포, 결함 후보 건수, V2 테이블 메타데이터
- `member-foundation-schema.sql`: 데이터가 없는 schema-only dump
- 두 산출물은 GitHub Actions artifact로만 보관하며 기본 보존 기간은 3일이다.
- 원본 데이터 dump와 운영 자격증명은 저장소, Actions artifact, Notion에 저장하지 않는다.

2026-08-19 최초 실행 결과는 `docs/reports/member-foundation-production-audit-20260819.md`에 기록했다. 일회성 실행 워크플로는 감사 완료 후 제거했다.

## 익명화 스냅샷 전략

운영 V2 upgrade rehearsal에는 원본 행 dump 대신 다음 2단계를 사용한다.

1. schema-only dump로 운영 DDL과 인덱스·제약 조건을 재현한다.
2. 감사 결과의 provider/status/role 건수 분포를 바탕으로 무작위 이메일과 임의 provider subject를 가진 합성 fixture를 만든다.

운영 행을 포함한 dump가 반드시 필요하면 별도 승인 후 신뢰 구간 안에서만 비식별화한다. 이 경우 암호화 저장, 접근자 제한, 격리 DB 복원, 검증 종료 후 폐기를 필수로 하며 저장소와 Notion 반입을 금지한다.

## 검증 기준

- 목표 KPI: 감사 쿼리 성공률 100%, 운영 변경 쿼리 0개, 직접 식별자 노출 0건, V2 schema-only 추출 성공률 100%.
- OKR 연결: V3 identity 전환에서 기존 로그인 데이터 유실과 account takeover 위험을 배포 전에 제거한다.
- 평가셋: Flyway history 1회, `users` 품질 집계 8종, token orphan 집계 1종, V2 컬럼·인덱스·제약 조건, schema-only dump 1회.
- Before: V2 적용 사실과 전체 백업은 확인됐지만 provider/status 분포 및 backfill 결함 후보 집계 증거가 없음.
- After 목표: Flyway 현재 version 2, 실패 migration 0, normalized email/provider subject duplicate 0, social subject 누락 0, token orphan 0.
- 합격 기준: 위 결함 지표가 모두 0이고 모든 기존 행이 V3 backfill 규칙으로 설명된다. 0이 아닌 지표는 행 원문을 추출하지 않고 별도 제한된 정정 계획으로 이관한다.

## 재실행 절차

1. 수동 실행 전 워크플로가 `workflow_dispatch` 전용인지 확인한다.
2. 운영 앱 서버의 `/etc/devjobcollector/devjobcollector.env`에서 접속값을 프로세스 안에서만 읽는다.
3. SQL 실행 결과에 이메일, 이름, subject, hash가 포함되지 않았는지 확인한다.
4. artifact를 내려받아 합격 기준을 판정한다.
5. 일회성 워크플로를 저장소에서 제거하고 Actions artifact는 3일 안에 만료시킨다.

## 금지사항

- `INSERT`, `UPDATE`, `DELETE`, `ALTER`, `DROP`, `TRUNCATE` 실행 금지
- 실제 DB 주소, 계정명, 비밀번호, 토큰을 문서나 로그에 기록 금지
- 운영 전체 dump를 개발 PC, 저장소, Notion으로 반출 금지
- 집계 이상을 조사한다는 이유로 이메일·provider subject 원문 출력 금지
