# 직무 제안 일괄 분석

`analyze-job-role-candidates.mjs`는 공개 공고 API에서 받은 JSON 배열 또는 `content` 배열을 입력으로 받아, 공개 카드에서 제목을 그대로 표시하는 공고에 한해 관리자 분석 API를 순차 호출한다. 결과는 검토 대기 제안이며 승인 또는 공개하지 않는다. 관리자 권한, CSRF, 서버의 출처 허용 목록과 활성화 설정이 필요하다. 본문은 서버가 기존 정책에 따라 AI에 전송한다.

입력 파일을 프로젝트 내부에 보관하고 외부 공유하지 않는다. 관리자 세션과 CSRF는 안전한 환경변수로만 전달하고, 터미널 기록이나 파일에 저장하지 않는다. 환경변수 `DJC_ADMIN_API_BASE`는 HTTPS 관리자 API 경로(`/api/v1/admin`), `DJC_ADMIN_COOKIE`는 관리자 세션 쿠키, `DJC_ADMIN_CSRF`는 CSRF 값이다. 인증정보는 출력하지 않는다.

```powershell
Get-Content -Raw <snapshot.json> | node ops/ai/analyze-job-role-candidates.mjs
```

결과의 `snapshotHash`와 `nextOffset`을 보관하면 동일한 스냅샷에서 `DJC_ROLE_SNAPSHOT_HASH`와 `DJC_ROLE_CANDIDATE_OFFSET`으로 재개할 수 있다. 목록이 바뀐 경우 처음부터 다시 시작한다. 기존에 처리한 입력은 서버에서 중복 호출을 차단한다. 직무·분야 분석은 UTC 기준 하루 최대 40회·$0.10 예약 한도를 공유한다. 자동 분야 배치는 전체 사용량이 20회 또는 $0.05에 도달하면 멈춰 나머지를 수동 분석에 남긴다. 실패한 호출도 예산을 사용한다. 호출 실패는 일괄 실행을 중단하므로 이력과 한도를 확인하고 동일 입력으로 다시 시작한다.

검증: `node --test ops/ai/analyze-job-role-candidates.test.mjs frontend/src/utils/jobCardRoles.test.js frontend/src/utils/jobRoleCandidates.test.js`
