# Career 활동 동시성·소유권 검증 결과

> 검증일: 2026-08-27 KST  
> 대상: CH-P3-06 북마크·지원·최근 조회 동시 요청 및 회원 소유권  
> 최종 구현 커밋: `d4cc066`

## 목표와 합격 기준

- **목표 KPI**: 동일 회원·공고에 대한 20회 요청(concurrency 10) 후 북마크 1행, 지원 1행, 최근 조회 1행과 `view_count=20`; 타 회원 데이터 노출·변경 0건; 평가셋 오류율 0%.
- **OKR 연결**: Career Hub 회원 활동의 데이터 유실·중복 0건과 무권한 접근 허용 0건을 보장해 지속적인 커리어 관리 여정을 지원한다.
- **평가셋**: 공식 `mysql:26.7.0` 임시 컨테이너, Flyway V1→V4, 요청 20회/concurrency 10, 서로 다른 회원 2명과 공고 1건. 북마크·지원·최근 조회 동시 쓰기 및 타 회원 목록·삭제·상태 변경을 검증한다.
- **합격 기준**: 동시 쓰기 3/3, 소유권 차단 5/5, MySQL 전체 게이트 10개 클래스, Docker/Backend Actions, 운영 smoke가 모두 실패 0건이어야 한다.

## Before / After

| 구분 | Before | After |
|---|---|---|
| 동시 upsert 결과 조회 | MySQL REPEATABLE READ 스냅샷에서 다른 트랜잭션이 생성한 행을 못 찾아 예외 발생 | upsert 후 `SELECT ... FOR UPDATE` 현재 읽기로 결과 확인 |
| 동일 회원 lock 순서 | 북마크 insert 경합에서 MySQL 1213 deadlock 검출 | 회원 행을 먼저 잠근 뒤 활동 행을 처리해 lock 순서 `user → activity`로 통일 |
| 북마크 20회 | 평가셋 실패 | 1행, 예외 0건 |
| 지원 20회 | 미보장 | 1행, 예외 0건 |
| 최근 조회 20회 | 단건 멱등만 검증 | 1행, `view_count=20` |
| 타 회원 접근 | 서비스별 단위 검증 | 통합 평가셋에서 목록 0건, 삭제·상태 변경 404 |

초기 실패는 평가셋 커밋 `22773ce`의 Actions `32979408884`, `32979408959`에서 검출됐다. 현재 읽기 수정 `1d134f2` 이후 로컬 MySQL에서 deadlock을 추가로 재현했으며, 회원 행 선잠금 수정 `d4cc066`으로 최종 해결했다. 실패 시 Backend workflow는 배포 단계 전에 중단됐다.

## 구현

- `CareerActivityConcurrencyIntegrationTest`: 실제 Spring Security/JPA/MySQL 경로로 동시 요청과 회원 경계를 검증한다.
- `run-member-migration-tests.sh`: 기존 MySQL 게이트에 동시성 평가셋을 포함한다.
- `JobBookmarkRepository`, `JobApplicationRepository`: 회원 행 잠금과 owner/job 현재 읽기를 제공한다.
- `JobBookmarkService`, `JobApplicationService`: 동일 회원 활동 쓰기를 회원 행 기준으로 직렬화한다. 회원이 다른 요청은 서로의 회원 행을 잠그지 않는다.

## 검증 결과

| 평가 | 결과 |
|---|---|
| 격리 `CareerActivityConcurrencyIntegrationTest` | 3/3 통과, 34초 |
| MySQL 전체 migration/integration gate | 10개 클래스 통과, `BUILD SUCCESSFUL`, 1분 13초 |
| Docker image validation | Actions `32992023748` 성공 |
| Backend test/deploy | Actions `32992027251` 성공 |
| 운영 health | HTTP 200 |
| 운영 공개 검색 | HTTP 200 |
| 운영 활동 API 3종 무토큰 | bookmarks/applications/recent-jobs 모두 HTTP 401 |

운영 확인 명령은 도메인을 마스킹해 기록한다.

```powershell
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/actuator/health
curl.exe -sS -o NUL -w "%{http_code}" "https://<API_DOMAIN>/api/v1/jobs/search?size=1"
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/api/v1/members/me/bookmarks
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/api/v1/members/me/applications
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/api/v1/members/me/recent-jobs
```

## 판정과 잔여 범위

CH-P3-06 합격 기준을 모두 충족했다. CH-P3 회원 활동 API와 프론트 연동 범위는 완료다.

다음 독립 작업은 CH-P4-01의 메모리 기반 `ResumeService` 제거 계획 및 API 계약 확정이다. 실제 로그인 토큰 기반 회원명 200 확인과 CH-R1 viewport·키보드·OAuth 수동 QA는 별도 미완료 항목으로 유지한다. Notion 원문은 기존 API 404로 이번 상태를 동기화하지 못했다.

GitHub 장애 중 구 커밋 `1d134f2`에 생성된 실행 `32984237650`, `32984237629`는 API상 `queued`지만 job 수가 0이고 cancel/force-cancel 요청을 수락하지 않는 비정상 레코드로 남아 있다. 실제 job이 생성될 경우 이전 Backend 배포를 즉시 취소하고 `d4cc066` 배포를 재실행해야 한다.
