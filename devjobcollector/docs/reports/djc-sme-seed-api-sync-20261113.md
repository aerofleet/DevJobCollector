# DJC 국내 중소기업 Seed API 동기화

## 목적과 KPI

- **OKR 연결**: 중소기업·스타트업 채용공고 비중을 늘려 대기업 중심 데이터 편중을 완화한다(Notion 전략 2.1, Phase 1).
- **목표 KPI**: 일일 동기화 성공률 99% 이상, 최신 동기화 데이터 26시간 이내, API `totalCount` 대비 처리 누락 0건, 처리 시간 30분 이내.
- **평가셋**: 중진공 API의 최신 스냅샷 전체 페이지(`page`/`perPage`/`totalCount`/`currentCount`), IT·개발 직무 라벨 200건 수동 검수셋. API 레코드는 회사 Seed 후보로만 반영하며 채용공고나 수집 Target으로 직접 승격하지 않는다.
- **합격 기준**: 모든 API 페이지가 완료되고 누적 `currentCount`가 `totalCount`와 일치, 유효/건너뜀 레코드 수가 각 페이지 응답 행 수와 일치, 사업자번호 원문 미저장, 기존 검수 상태 보존, 분류셋 IT 직무 precision 90% 이상.
- **Before/After**: Before는 연간 CSV의 수동 업로드; After 목표는 승인 API에서 매일 증분 없이 전체 idempotent upsert. 실제 API 동기화 전후 수치는 아직 미측정.

## 구현 내용

- 최신 Swagger 스냅샷: `2026-03-06`, path `/15100246/v1/uddi:bb241c38-4ecd-4b5f-aa09-88f2e9478860` ([Swagger](https://infuser.odcloud.kr/oas/docs?namespace=15100246/v1)).
- `Authorization: Infuser {API_KEY}` 헤더 인증을 사용한다. 키는 요청 URL이나 로그에 남기지 않는다.
- `page`, `perPage`, `returnType=JSON` 페이지를 1일 간격으로 읽어 Seed에 최대 1,000건씩 멱등 반영한다.
- 사업자번호는 해시만 저장하고 기존 Seed 상태는 유지한다. IT·개발 관련 공고명은 우선순위와 분류 근거로 기록한다.
- 운영 키는 GitHub Actions secret `DATA_API_SERVICE_KEY`로 별도 관리한다. 배포 workflow가 `/etc/devjobcollector/devjobcollector.env`에 전달하고 Spring 프로세스가 환경변수로 읽는다. 기존 `API_SERVICE_KEY`와 혼용하지 않는다.
- 운영 자동 실행은 GitHub Actions 변수 `DJC_SME_JOB_COMPANY_SYNC_ENABLED=true`로 명시적으로 켠다. 배포 workflow가 Spring 환경변수 `SME_JOB_COMPANY_SYNC_ENABLED`로 전달하며, 기본값은 `false`다.

## 현재 검증 상태

- Gradle `compileJava`, `compileTestJava`: 성공.
- API 응답 매핑·`Authorization: Infuser` 헤더, 직무 분류 단위 테스트 2건: 성공.
- 관리자 프런트엔드 `npm.cmd run lint`, `npm.cmd run build`: 성공.
- 인증값 수정 후 `.env.docker`의 키로 `Authorization: Infuser {API_KEY}` 1페이지 호출이 HTTP 200, `currentCount=10`, `totalCount=84,860`으로 확인됐다. 2026-11-14 Actions 배포 #175는 테스트·마이그레이션·컨테이너 헬스체크를 통과했고, 로그에서 `DATA_API_SERVICE_KEY` 전달 및 `SME_JOB_COMPANY_SYNC_ENABLED=true`를 확인했다. 운영 전체 페이지 완료 로그와 DB Seed 건수 검증은 아직 미완료다.
- 승인된 실제 키로 최신 API 전체 페이지 평가셋 실행, Before/After 수치, KPI 합격 여부: 미완료.
