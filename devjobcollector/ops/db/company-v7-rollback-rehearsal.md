# Company V7 애플리케이션 롤백 절차

> 대상 런타임: JAR + systemd
> 원칙: V7 `security_audit_events`는 삭제하지 않고 애플리케이션만 직전 V6-era JAR로 되돌린다.

## 목표와 합격 기준

- **목표 KPI**: V7 적용 후 직전 애플리케이션 기동 성공 1/1, 기업 Repository 회귀 5/5, Flyway V1~V7 성공 이력 보존 7/7, V7 감사 테이블 보존 1/1.
- **OKR 연결**: 기업회원 MVP 배포 실패 시 회원·기업 데이터와 감사 증적을 잃지 않고 서비스 복구 경로를 확보한다.
- **평가셋**: MySQL 26.7, V1→V7 적용 DB, V7 도입 직전 커밋의 애플리케이션, 기업 생성·membership·OWNER invariant·권한 조회 5건.
- **합격 기준**: 이전 JAR이 V7 DB에서 시작되고 5건이 모두 통과하며, `flyway_schema_history`와 `security_audit_events`가 유지된다.

## 배포 전 준비

1. 현재 실행 JAR과 직전 정상 JAR의 checksum을 기록한다.
2. DB 백업 또는 복구 지점을 생성하고 복구 식별자를 비공개 운영 기록에 남긴다.
3. 아래 읽기 전용 쿼리로 V7 성공 이력과 감사 테이블을 확인한다.

```sql
SELECT version, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'security_audit_events';
```

## 애플리케이션 롤백

```bash
sudo systemctl stop devjobcollector.service
sudo cp <PREVIOUS_RELEASE_JAR> <APP_JAR_PATH>
sudo systemctl start devjobcollector.service
sudo systemctl is-active devjobcollector.service
curl --fail --silent --show-error https://<API_DOMAIN>/actuator/health
```

- V7은 기존 테이블을 변경하지 않는 additive migration이므로 롤백 중 `DROP TABLE`, Flyway history 삭제 또는 `repair`를 수행하지 않는다.
- 이전 JAR은 V7을 future migration으로 두고 기존 V1~V6 Entity만 사용한다.
- 롤백 뒤 기업 가입·조회 API와 공개 검색 API를 읽기/최소 쓰기 smoke로 확인한다.

## 실패 시 중단 조건

- 이전 JAR이 Flyway validation 또는 JPA validation에서 기동하지 못하면 트래픽을 열지 않는다.
- V1~V7 성공 이력 7개 중 하나라도 누락되거나 V7 감사 테이블이 사라지면 DB 복구 절차로 전환한다.
- DB DDL을 역방향으로 변경해야 하는 상황이면 별도 승인과 복구 리허설 없이는 진행하지 않는다.

## 2026-09-12 리허설 결과

- MySQL 26.7에서 V1→V7 성공 이력 7/7과 `security_audit_events` 1/1을 확인했다.
- V7 DB에 V6-era 커밋 `c3072d5`를 연결해 `CompanyRepositoryIntegrationTest` 5/5를 통과했다.
- 리허설 후 Flyway 이력 7/7과 V7 감사 테이블 1/1이 유지됐다.
