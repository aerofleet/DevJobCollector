# DJC 초기 SUPER_ADMIN 프로비저닝 런북

## 목적과 안전 조건

관리자 계정이 0명인 환경에 최초 `SUPER_ADMIN` 한 명을 생성한다. 기능은 기본 비활성화이며, 동일 이메일의 `SUPER_ADMIN`이 이미 있으면 변경 없이 종료한다. 다른 관리자 계정이 하나라도 존재하면 신규 생성을 거부한다.

- 운영 적용은 단일 app replica에서 수행한다.
- 비밀번호는 16~128자이며 제어 문자를 포함하지 않는다.
- 비밀번호 원문을 Git, 명령 기록, 로그, Notion에 남기지 않는다.
- TOTP seed 원문은 비밀번호와 동일하게 취급하며, DB에는 AES-256-GCM 암호문만 저장한다.
- `ADMIN_MFA_ENCRYPTION_KEY`는 base64 인코딩된 32바이트 키이며 bootstrap 종료 후에도 유지한다.
- 운영 환경 파일 권한은 소유자 읽기/쓰기만 허용한다.
- 성공 직후 bootstrap 환경변수를 제거하고 app을 다시 배포한다.

## 사전 확인

1. Flyway 최신 성공 버전이 `8`인지 확인한다.
2. `admin_accounts`가 0행인지 확인한다.
3. DB 백업 및 이전 app image tag를 확인한다.
4. 운영 app replica가 1개인지 확인한다.

```sql
SELECT version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;

SELECT COUNT(*) AS admin_count FROM admin_accounts;
```

## 실행

운영 비밀 저장소에서 일회용 비밀번호를 생성하고 `/etc/devjobcollector/devjobcollector.env`에 다음 값을 잠시 추가한다. 실제 값은 아래 플레이스홀더를 대체한다.

```dotenv
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_EMAIL=<ADMIN_EMAIL>
ADMIN_BOOTSTRAP_NAME=<ADMIN_NAME>
ADMIN_BOOTSTRAP_PASSWORD=<ONE_TIME_PASSWORD>
ADMIN_BOOTSTRAP_MFA_SECRET=<BASE32_TOTP_SECRET>
ADMIN_MFA_ENCRYPTION_KEY=<BASE64_32_BYTE_KEY>
```

기존 배포 절차로 app을 재생성한다. 성공 로그에는 이메일이나 비밀번호가 출력되지 않고 다음 고정 문구만 기록된다.

```text
Initial SUPER_ADMIN provisioning completed
```

## 검증

```sql
SELECT id, email, role, status,
       password_hash LIKE '$2%' AS bcrypt_hash,
       mfa_secret_ciphertext IS NOT NULL AS mfa_encrypted
FROM admin_accounts;

SELECT action, target_type, target_id, result, request_id
FROM admin_audit_logs
WHERE action = 'ADMIN_BOOTSTRAP'
ORDER BY id DESC
LIMIT 1;
```

합격 조건은 `SUPER_ADMIN/ACTIVE` 1행, BCrypt hash 확인값 `1`, MFA 암호문 확인값 `1`, `ADMIN_BOOTSTRAP/SUCCESS` 감사 1행이다. 비밀번호 또는 TOTP seed 원문 비교 쿼리는 실행하지 않는다.

검증 직후 환경 파일에서 `ADMIN_BOOTSTRAP_*` 다섯 줄을 제거하고 app을 다시 배포한다. `ADMIN_MFA_ENCRYPTION_KEY`는 세션 운영에 필요한 지속 비밀값이므로 제거하지 않는다. 최종 컨테이너 환경에 `ADMIN_BOOTSTRAP_PASSWORD`와 `ADMIN_BOOTSTRAP_MFA_SECRET`이 없는지 확인한다.

## 실패 처리

- 관리자 행이 이미 존재한다는 오류: 자동 우회하지 말고 대상 환경과 기존 계정을 확인한다.
- 동일 이메일이 비-SUPER_ADMIN이라는 오류: DB에서 역할을 직접 변경하지 말고 관리자 계정 복구 절차로 전환한다.
- DB 또는 감사 저장 실패: 트랜잭션이 전체 롤백되므로 원인을 수정한 뒤 `admin_accounts=0`을 재확인한다.
- 생성 후 환경변수 제거 전 장애: 먼저 bootstrap 변수를 제거해 재배포하고 계정·감사 행을 확인한다.

## KPI / OKR / 평가셋

- **OKR 연결**: 운영 DB 수동 변경 없이 추적 가능한 관리자 접근 경로를 개설한다.
- **목표 KPI**: SUPER_ADMIN 생성 1행, 감사 기록 1행, 평문 비밀번호·TOTP seed 저장 및 로그 출력 0건, 재실행 추가 생성 0건.
- **평가셋**: 단위 8건, MySQL 26.7 최초 생성·BCrypt·감사·재실행 통합 1건.
- **Before**: 초기 관리자 생성 경로 0개, 수동 SQL 외 감사 가능한 절차 없음.
- **After**: 기본 비활성 one-shot runner 1개, fail-closed 조건 3개, 운영 검증 쿼리 2개.
- **합격 기준**: 단위 8/8, MySQL 프로비저닝 통합 1/1, 원문 secret assertion 0건.
