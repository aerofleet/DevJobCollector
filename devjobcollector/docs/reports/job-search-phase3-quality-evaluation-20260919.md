# 공고 검색 Phase 3 품질 평가 및 CI 게이트 결과

- 기준일: 2026-09-19
- 대상: `/api/v1/jobs/search` 키워드 검색
- 선행 결과: Phase 1 전체 필드 검색, Phase 2 동의어·근거 스니펫·관측 지표

## 1. 목표와 OKR 연결

- 목표 KPI
  - Recall@20 `>= 0.85`
  - nDCG@10 `>= 0.80`
  - 무결과율 `< 0.05`
  - 활성 공고 1,000건 기준 DB 검색 p95 `< 300ms`
- OKR 연결
  - 검색 실패로 인한 공고 탐색 이탈을 줄이고 개발자 직무·기술 스택·공고 본문 발견률을 높인다.
  - 검색 품질 회귀를 배포 전 MySQL 게이트에서 자동 차단한다.

## 2. 평가셋 정의

파일: `src/test/resources/search/job-search-evaluation-set.csv`

| 구분 | 질의 수 | 측정 대상 |
|---|---:|---|
| 한글·영문 혼합 | 30 | 대소문자, 한글 직무명, 영문 기술명 조합 |
| 복합 검색어 | 30 | 제목·직무·지역·본문·기술 태그를 가로지르는 AND 검색 |
| 개발자 동의어 | 20 | backend/server, k8s/Kubernetes, AI/인공지능 등 |
| 공고문 본문 전용 | 20 | 제목에 없는 자격요건·채용절차·고용형태 검색 |
| 합계 | 100 | 관련 문서 ID와 1~3 등급 relevance label |

평가 corpus는 14개 합성 공고로 고정했다. 측정은 MySQL `26.7.0`에서 실제 Querydsl/JPA 검색 쿼리를 실행하며, 각 질의의 상위 20개 결과로 Recall@20, 상위 10개 결과로 nDCG@10을 계산한다. 이 평가셋은 결정적 회귀 검사용이며 운영 사용자 클릭 로그 기반 평가셋은 아니다.

## 3. 발견 결함과 조치

최초 100질의 실행에서 `artificial intelligence`가 공백 기준으로 두 토큰으로 분리되어 `AI/인공지능` 동의어 그룹으로 확장되지 않았다.

- 최초 결과: Recall@20 `0.990`, nDCG@10 `0.980`, 무결과율 `0.010`
- 조치: 공백·하이픈을 포함하는 알려진 표현을 토큰화 전에 단일 동의어 개념으로 정규화
  - `artificial intelligence`
  - `machine learning`
  - `spring boot`, `spring-boot`
  - `full stack`, `full-stack`
  - `front-end`, `back-end`
- 회귀 테스트: 다단어·하이픈 동의어가 각각 하나의 AND term으로 유지되는 단위 테스트 추가

## 4. 최종 결과

| KPI | Before | After | 합격 기준 | 판정 |
|---|---:|---:|---:|---|
| Recall@20 | 0.990 | **1.000** | >= 0.85 | 통과 |
| nDCG@10 | 0.980 | **0.990** | >= 0.80 | 통과 |
| 무결과율 | 0.010 | **0.000** | < 0.05 | 통과 |
| DB 검색 p95 | 49ms | **45ms** | < 300ms | 통과 |

성능 평가 조건은 활성 공고 1,000건, warm-up 5회, 측정 30회, `backend k8s`, 페이지 크기 20이다. 최종 p50 34ms, p95 45ms, max 47ms였다.

## 5. CI 연결

`ops/db/run-member-migration-tests.sh`에 아래 테스트를 추가했다.

- `JobSearchRepositoryIntegrationTest`
- `JobSearchQualityEvaluationIntegrationTest`

이 스크립트는 저장소 루트의 두 GitHub Actions에서 이미 실행된다.

- `.github/workflows/djc-docker-ci.yml`
- `.github/workflows/djc-backend-deploy.yml`

따라서 검색 구현 또는 평가셋 변경 시 MySQL 26.7에서 KPI 미달이 발생하면 이미지 빌드·배포 전에 실패한다.

## 6. 검증 명령과 결과

```powershell
& 'C:\Program Files\Git\bin\bash.exe' ops/db/run-member-migration-tests.sh
```

- MySQL 게이트: 94 tests, failures 0, errors 0, skipped 0
- 검색 품질: 100질의, Recall@20 1.000, nDCG@10 0.990, 무결과율 0.000
- 검색 성능: p50 34ms, p95 45ms, max 47ms

```powershell
.\gradlew.bat test --no-daemon
```

- root: 498 tests, 404 passed, 94 조건부 MySQL 테스트 skipped, failures/errors 0
- auth-common: 26 tests, 26 passed, failures/errors/skipped 0
- 합계: 524 tests, 430 passed, 94 skipped, failures/errors 0

```powershell
git diff --check
```

- whitespace 오류 0건
- Windows 줄바꿈 변환 경고만 존재

## 7. 완료 판정과 다음 측정

100질의 고정 평가셋의 세 품질 KPI와 1,000건 성능 KPI를 모두 충족했으므로 Phase 3를 완료로 판정한다.

운영에서는 `djc.jobs.search.requests`의 `outcome=empty` 비율과 `djc.jobs.search.duration`을 지속 관찰한다. 활성 공고가 5,000건에 도달하거나 DB p95가 300ms 이상이면 MySQL FULLTEXT 또는 별도 검색 인덱스 도입을 다시 평가한다. 실제 사용자 질의·클릭 로그가 확보되면 개인정보를 제거한 운영 평가셋으로 본 합성 평가셋을 보완한다.
