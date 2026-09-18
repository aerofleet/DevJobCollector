# DJC 공고 검색 품질 개선 2단계 검증

## 1. 범위

- 개발자 용어 동의어 확장
  - 백엔드/backend/back-end/server
  - 프론트엔드/frontend/front-end
  - 풀스택/fullstack/full-stack
  - 스프링/spring/springboot/spring boot
  - 쿠버네티스/kubernetes/k8s
  - 머신러닝/machine learning/ml
  - 인공지능/artificial intelligence/ai
  - 데브옵스/devops/sre
- 검색 결과 DTO에 최대 180자 `matchedSnippet` 추가
- 공고 카드에 본문 일치 스니펫 2줄 표시
- 공급처별 본문·전형·고용형태·기술태그 보유율 집계 SQL 추가
- 실제 검색어를 노출하지 않는 bounded query/outcome 검색 metric 추가
- 운영 규모 근사 MySQL 검색 성능 평가
- 제외: 물리 검색 문서 테이블, FULLTEXT/n-gram 인덱스, 검색어 강조 마크업

## 2. KPI / OKR / 합격 기준

| 항목 | 목표 | 결과 | 판정 |
|---|---:|---:|---|
| 동의어·스니펫·metric 단위 평가 | 14/14 | 14/14 | 통과 |
| MySQL 26.7 검색 평가 | 6/6 | 6/6 | 통과 |
| 1,000건 검색 p95 | 300ms 미만 | 149ms | 통과 |
| 1,000건 검색 오류 | 0건 | 0건 | 통과 |
| 최근 운영 공고 본문 표본 | 95% 이상 | 100% (50/50) | 통과 |
| 운영 상세 조회 표본 오류 | 0건 | 0/50 | 통과 |
| 공고 목록 UI 평가 | 8/8 | 8/8 | 통과 |
| 전체 Backend 회귀 failures/errors | 0건 | 0/0 | 통과 |
| Frontend lint/build | 오류 0 / build 성공 | 오류 0 / 1,833 modules | 통과 |

- OKR 연결: 사용자의 표현과 저장된 공고 용어가 달라도 개발자 공고를 찾고, 결과 카드에서 일치 근거를 확인할 수 있게 한다.
- 합격 기준: 기능 평가셋 100%, MySQL p95 300ms 미만, 전체 회귀 실패 0, 4개 viewport UI 통과.

## 3. 평가셋과 결과

### 동의어·스니펫·metric 단위 평가 14건

- 1단계 정규화 6건
- 개발자 동의어 그룹 확장 1건
- 본문 동의어 일치, 전형절차 fallback, 불일치 null, 180자 제한 4건
- hit/empty/error와 blank/single/multi bounded tag, 검색어 비노출 3건

### MySQL 26.7 통합 평가 6건

- 상세 본문·전형·고용형태 검색
- 서로 다른 필드의 복합 AND 검색
- 제목 우선 관련도 정렬
- 서로 다른 기술스택 태그의 복합 검색
- `k8s` 입력으로 `Kubernetes` 본문 검색
- 활성 공고 1,000건, warm-up 5회, 측정 30회 성능 평가

성능 결과:

| rows | requests | p50 | p95 | max | errors |
|---:|---:|---:|---:|---:|---:|
| 1,000 | 30 | 80ms | 149ms | 219ms | 0 |

### 운영 공개 API 기준선과 본문 표본

- 변경 전 운영 공개 검색 API 30회 순차 측정: p50 623ms, p95 1,311ms, max 4,036ms, 오류 0
- 조건: 인터넷 왕복·운영 애플리케이션 처리시간 포함, `Java`/`백엔드`/`Spring` 순환, size 12
- 최근 활성 공고 50건 상세 표본: 본문 50/50, 상세 조회 오류 0/50
- COMPANY_PAGE 8건: 본문 8/8, 평균 3,220자
- PUBLIC_ALIO 42건: 본문 42/42, 평균 250자, 전형절차 42/42
- 주의: 최근 50건 표본이며 전체 982건과 개별 ATS 공급처를 대표하지 않는다.

### UI 평가 8건

- mobile-360, tablet-768, desktop-1024, desktop-1440 각 2건
- 무한 스크롤 회귀, 검색 범위 안내, 복합 검색어 전달, 일치 스니펫 렌더링을 확인했다.

## 4. Before / After

| 항목 | Before | After |
|---|---|---|
| 용어 불일치 | 입력 문자열만 검색 | 개발자 동의어 그룹 내 OR 검색 |
| 검색 근거 | 카드에서 확인 불가 | 최대 180자 본문 스니펫 제공 |
| 데이터 품질 | 반복 가능한 집계 없음 | 공급처별 aggregate-only 감사 SQL |
| 성능 판단 | 운영 외부 p95만 존재 | MySQL 1,000건 DB 평가 p95 확보 |
| 운영 관찰 | 검색 전용 지표 없음 | 요청 수·지연시간에 bounded query/outcome tag |

## 5. FULLTEXT 결정

- 현재 운영 활성 공고는 982건이다.
- 동의어를 포함한 MySQL 26.7 1,000건 평가에서 p95 149ms로 목표 300ms를 충족했다.
- 따라서 V8 검색 문서 테이블과 FULLTEXT/n-gram 인덱스는 이번 단계에서 추가하지 않는다.
- 활성 공고 5,000건 또는 DB 검색 p95 300ms 초과 시 FULLTEXT 설계를 재개한다.
- 외부 왕복 p95 1,311ms는 DB 외 구간을 포함하므로 배포 후 서버 측 timing과 API p95를 별도로 관찰한다.

## 6. 검증 명령

```powershell
.\gradlew.bat test --tests kr.itsdev.devjobcollector.repository.JobSearchKeywordTest `
  --tests kr.itsdev.devjobcollector.service.JobSearchSnippetTest `
  --tests kr.itsdev.devjobcollector.monitoring.JobSearchMetricsTest

# 임시 mysql:26.7.0, 활성 공고 1,000건 fixture
.\gradlew.bat test `
  --tests kr.itsdev.devjobcollector.repository.JobSearchRepositoryIntegrationTest `
  --no-daemon

.\gradlew.bat test

cd frontend
npm run lint
npm run build
npx playwright test e2e/jobs-infinite-scroll.spec.js

# aggregate-only audit
mysql < ops/db/job-search-quality-audit.sql
git diff --check
```

- 전체 Gradle: 522건 중 429 passed / 93 기존 조건부 skip, failures/errors 0
- Frontend: lint 오류 0, production build 1,833 modules
- Playwright: 8/8
- 감사 SQL: MySQL 26.7 문법 실행 성공
- whitespace 오류: 0건

## 7. 다음 단계

- 배포 후 `djc.jobs.search.duration`, `djc.jobs.search.requests`로 p50/p95와 hit/empty/error 비율을 관찰한다. 실제 검색어는 metric tag로 수집하지 않는다.
- 전체 활성 공고에 `job-search-quality-audit.sql`을 실행해 공급처별 본문·기술태그 보유율을 확정한다.
- 사람이 관련도를 판정한 검색어 100건으로 Recall@20과 nDCG@10을 측정한다.
- FULLTEXT는 활성 공고 5,000건 또는 DB p95 300ms 초과 시 재검토한다.
