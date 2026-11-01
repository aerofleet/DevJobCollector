# 근무형태 후보 규칙 베이스라인 — 2026-11-01

## 평가 범위

- **목표 KPI**: 공개 라벨 coverage ≥30%; 독립 정답 평가 전 출시 금지.
- **OKR 연결**: 개발자가 사내 제품 공고를 찾고 SI/파견 공고를 걸러내는 부담을 낮춘다. 사용자 성과는 측정 전이다.
- **평가셋**: 전체 공개 목록 63페이지에서 제목에 개발 직무 표현이 있는 공고를 모으고, 기업별 round-robin으로 고른 200건. 기업 40곳, Greenhouse 142 / COMPANY_PAGE 29 / Kakao 17 / PUBLIC_ALIO 5 / Woowahan 3 / Lever 4. 상세 본문 200/200 수집 성공.
- **Before/After**: 이전 탐색 표본 200건에서 `UNKNOWN` 200건. 확장 평가 후보 200건에서도 `UNKNOWN` 200건, 자동 공개 라벨 0건이다. Coverage 0/200 = 0%.
- **결과**: 공개 coverage KPI 30% 미달. Precision/recall은 정답 라벨이 없어 미측정이며 통과로 볼 수 없다. 현재 규칙은 출시 부적합.
- **합격 기준**: 프로토콜의 기업 ≥30, 라벨별 precision/recall, cluster bootstrap CI, hard-negative 및 검토자 agreement 기준 전체 충족.

## 제한

이 표본은 제목 정규식과 회사별 추출에 따른 탐색 표본이며 활성 개발 직군의 확률 표본으로 검증되지 않았다. Greenhouse가 71%를 차지한다. 검색 제목·회사 수만으로 SME 여부를 구분하지 않는다. `UNKNOWN` 출력은 그 공고가 사내 개발이라는 정답을 뜻하지 않는다. 따라서 이 결과는 규칙의 coverage 베이스라인만 평가하며, 의미 정확도나 시장 공급량 KPI를 측정하지 않는다.

## 산출물

- Snapshot: `djc-developer-all-pages-200-20261101.csv` (작업 PC 로컬 임시 보관, Git 미포함)
- 균형 라벨 시트: `work-arrangement-labels-v6.csv` (`.codex` 로컬 보관, 정답 칸 비어 있음, Git 미포함)
- 규칙: `ops/tools/predict-work-arrangement.py`
- 정답 입력 뒤 결합: `ops/tools/build-work-arrangement-evaluation.py`
- KPI 집계: `ops/tools/evaluate-work-arrangement.py`
