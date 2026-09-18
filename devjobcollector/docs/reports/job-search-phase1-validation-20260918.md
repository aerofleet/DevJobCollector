# DJC 공고 검색 품질 개선 1단계 검증

## 1. 범위

- 상세 검색 범위에 `applyQual`, `processInfo`, `hireType` 추가
- NFKC·소문자 변환과 공백·`-`·`.`·`/`·`_`·`·`·`,` 기준 토큰화
- 중복 제거 후 최대 8개 검색어를 AND로 결합
- 토큰별 제목 100, 기술스택 80, 직무 60, 회사 40, 본문 20 가중치 정렬
- 검색 UI 안내를 `직무, 기술 스택, 공고 내용 검색`으로 변경
- 제외: 개발자 동의어 사전, 검색 전용 문서, MySQL FULLTEXT, 결과 스니펫

## 2. KPI / OKR / 합격 기준

| 항목 | 목표 | 결과 | 판정 |
|---|---:|---:|---|
| 검색어 정규화 단위 평가셋 | 6/6 | 6/6 | 통과 |
| MySQL 26.7 기능 평가셋 | 4/4 | 4/4 | 통과 |
| 공고 목록 UI 평가셋 | 8/8 | 8/8 | 통과 |
| Backend 회귀 failures/errors | 0건 | 0/0 | 통과 |
| Frontend lint/build | 오류 0 / build 성공 | 오류 0 / 1,833 modules | 통과 |
| 검색 API p95 | 300ms 미만 | 운영 유사 데이터 미측정 | 보류 |

- OKR 연결: 본문에만 존재하는 개발 기술과 복합 조건을 검색할 수 있게 해 개발자 공고 탐색 성공률을 높인다.
- 1단계 합격 기준: 기능 평가셋 100%, 전체 회귀 실패 0, UI 4개 viewport 통과.
- 2단계 진입 기준: 공급처별 본문 보유율과 운영 유사 데이터 검색 p95를 측정한다.

## 3. 평가셋

### 단위 평가 6건

1. 전각 영문·대소문자·개발자 구분자를 정규화한다.
2. `C++`, `C#` 기호는 유지하고 `Node.js`는 `node`, `js`로 분리한다.
3. 중복 토큰을 입력 순서대로 한 번만 유지한다.
4. 최대 8개 고유 토큰으로 쿼리 복잡도를 제한한다.
5. 중복 토큰은 8개 제한을 소모하지 않는다.
6. 구분자만 있는 입력은 빈 검색으로 처리한다.

### MySQL 26.7 통합 평가 4건

1. 상세 본문·전형 절차·고용 형태에만 있는 검색어를 찾는다.
2. `JAVA/Kafka`를 정규화하고 제목과 본문에 분산된 두 토큰을 모두 충족한다.
3. 동일 키워드에서 제목 일치 공고가 본문 전용 일치 공고보다 먼저 나온다.
4. `Java`, `Spring`이 서로 다른 기술스택 태그에 있어도 복합 검색어를 충족한다.

### UI 평가 8건

- mobile-360, tablet-768, desktop-1024, desktop-1440 각 2건
- 무한 스크롤 회귀와 새 검색 범위 안내·복합 검색어 API 전달을 확인한다.

## 4. Before / After

| 항목 | Before | After |
|---|---|---|
| 상세 본문 검색 | 대상 제외 | `applyQual`, `processInfo` 포함 |
| 고용 형태 검색 | 대상 제외 | `hireType` 포함 |
| 복합 검색어 | 전체 문자열 1회 LIKE | 고유 토큰별 OR-field, 토큰 간 AND |
| 입력 정규화 | 소문자 변환만 | NFKC·소문자·구분자 토큰화 |
| 결과 순위 | 등록일/마감일만 | 관련도 우선 후 기존 정렬 |
| UI 안내 | 포지션·회사명·기술스택 | 직무·기술스택·공고 내용 |

## 5. 검증 명령과 결과

```powershell
.\gradlew.bat test --tests kr.itsdev.devjobcollector.repository.JobSearchKeywordTest
.\gradlew.bat test

# 임시 mysql:26.7.0 컨테이너에서 DJC_MIGRATION_TEST_URL 설정 후
.\gradlew.bat test `
  --tests kr.itsdev.devjobcollector.repository.JobSearchRepositoryIntegrationTest `
  --no-daemon

cd frontend
npm run lint
npm run build
npx playwright test e2e/jobs-infinite-scroll.spec.js
git diff --check
```

- 검색 정규화: 6/6, failures/errors 0
- MySQL 26.7 검색 통합: 4/4, failures/errors 0
- 전체 Gradle: 511건 중 421 passed / 90 기존 조건부 skip, failures/errors 0
- 공고 목록 Playwright: 8/8, failures 0
- Frontend: lint 오류 0, production build 1,833 modules
- whitespace 오류: 0건

## 6. 잔여 위험과 다음 단계

- 현재 본문 검색은 `LONGTEXT LIKE '%token%'`이므로 데이터 증가 시 full scan 비용이 커진다.
- 공급처별 `applyQual` 보유율을 집계해 상세 검색의 실제 커버리지를 측정해야 한다.
- 운영 유사 데이터에서 p50/p95와 0건 검색 비율을 측정하기 전 성능 KPI는 완료 처리하지 않는다.
- 2단계에서 검색 전용 문서·개발자 동의어 사전·결과 스니펫을 구현한다.
- 3단계 FULLTEXT/n-gram 도입은 1단계 p95와 실행 계획을 근거로 결정한다.
