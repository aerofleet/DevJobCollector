# DJC 회원 기반 운영 DB 감사 결과 — 2026-08-19

## 판정

P0-01 운영 DB/Flyway V2 감사와 비식별 스냅샷 준비를 통과했다. 운영 데이터 변경 쿼리는 실행하지 않았고 사용자 직접 식별자는 산출물에 포함되지 않았다.

- 성공 실행: GitHub Actions run `32260566687`
- Flyway: baseline version 1 성공, V2 `djc add personal member signup` 성공
- 운영 base table: 11개 (`기존 DJC 8 + 회원 2 + flyway_schema_history 1`)
- `users`: 0건
- `email_verification_tokens`: 0건
- normalized email duplicate: 0그룹
- provider subject duplicate: 0그룹
- social identity subject 누락: 0건
- LOCAL password hash 누락: 0건
- social 계정 password hash 보유: 0건
- 이메일 인증 token orphan: 0건

운영 회원 데이터가 아직 없으므로 V3 운영 backfill 대상은 0건이다. 다만 migration test에서는 LOCAL, Google, GitHub 및 충돌 사례를 합성 fixture로 구성해 backfill 로직을 검증해야 한다.

## 스키마 계약 확인

운영 `users`, `email_verification_tokens`의 컬럼, 인덱스, 제약 조건은 저장소 V2와 일치했다.

- `users.email`: unique
- `users(provider, provider_user_id)`: unique
- `users.status`: index
- `email_verification_tokens(user_id, used_at, id)`: index
- token → user: foreign key, `ON DELETE CASCADE`는 schema-only DDL에서 확인

schema-only artifact에는 `INSERT`, `REPLACE`, `LOAD DATA` 문이 없었다.

## 산출물 무결성

산출물은 로컬 `.codex/tmp`와 3일 보존 Actions artifact에만 두며 저장소·Notion에는 원문을 올리지 않는다.

- 집계 감사 파일: 2,234 bytes, SHA-256 `FEE7EFA609D19E37027D85F8FB522969C115DD11C6E827799D06B358392C09DF`
- schema-only 파일: 13,167 bytes, SHA-256 `2A7366992CE9F81883667086B5CFAAA76A834856286AA492362F760A2A9760F1`
- 데이터 삽입문 검사: 0건
- 이메일 형식 및 bcrypt hash 패턴 검사: 0건

## 실행 중 발견 및 보완

1. 최초 실행 `32249008811`은 앱 계정에 `LOCK TABLES` 권한이 없어 schema-only dump가 실패했다.
2. DB 권한을 확대하지 않고 `--single-transaction --skip-lock-tables`로 수정했다.
3. 두 번째 실행 `32249585584`는 Docker stdin 미연결로 집계 파일이 0 bytes였으며 artifact 검수에서 발견했다.
4. `--interactive`와 `test -s`를 추가해 빈 감사 결과가 성공 처리되지 않도록 했다.
5. 최종 실행 `32260566687`은 감사, schema-only 추출, 직접 식별자 검사, artifact 업로드를 모두 통과했다.

## KPI / OKR / 평가셋

- 목표 KPI: 감사 쿼리 성공률 100%, 운영 변경 쿼리 0개, 직접 식별자 노출 0건, schema-only 추출 성공률 100%.
- OKR 연결: V3 identity 전환 전 운영 계약을 고정해 기존 로그인 데이터 유실과 account takeover 위험을 줄인다.
- 평가셋: Flyway history 1회, 회원 품질 집계 9종, V2 컬럼·인덱스·제약 조건, schema-only dump 1회, 데이터문·식별자 패턴 검사.
- Before: V2 적용 사실과 전체 백업만 확인됐고 V3 backfill 대상 및 결함 후보 집계 증거가 없었다.
- After: V3 운영 backfill 대상 0건, 모든 결함 지표 0, V2 스키마 계약 일치, schema-only 스냅샷 생성 완료.
- 합격 기준: Flyway version 2·실패 0, 결함 지표 0, 운영 변경 0, artifact 식별자 0 — 모두 충족.

## 다음 작업

P0-02에서 V3 컬럼 사전, 상태·에러 코드, LOCAL password credential 위치를 확정한다. 운영 데이터가 비어 있어 backfill 검증은 최소 다음 합성 fixture를 사용한다.

- LOCAL: 정상, password 누락
- Google/GitHub: 정상 subject, subject 누락
- normalized email 충돌
- 동일 provider subject 충돌
- 이메일 인증 전/후 상태
