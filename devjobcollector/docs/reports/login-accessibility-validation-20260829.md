# 로그인 접근성 평가 결과

## 범위

- 대상: Career Hub 로그인 화면 `/login?next=/member`
- 항목: 키보드 탐색 순서, focus 표시, 모바일 touch target
- viewport: 360×800, 768×1024, 1024×768, 1440×900

## KPI / OKR / 평가셋

- 목표 KPI: 핵심 컨트롤 키보드/focus 4/4 viewport 통과, 모바일 핵심 조작 영역 10/10이 44×44px 이상
- OKR 연결: CH-R1-03 접근성 수동 QA 이전에 재현 가능한 회귀 게이트 확보
- 평가셋:
  - 키보드: 아이디 → 비밀번호 → 로그인 유지 → 아이디 저장 → 로그인 → 아이디 찾기 → 비밀번호 찾기 → 회원가입 → Google → GitHub
  - touch target: 입력 2개, 체크박스 그룹 2개, 로그인 버튼, 찾기 링크 2개, 회원가입, 소셜 로그인 2개
- 합격 기준: focus 순서·표시 10/10 × 4 viewport, mobile-360 touch target 10/10, 가로 overflow 0px 유지

## Before

- 키보드 탐색·focus: 4/4 viewport 통과
- touch target: 10개 중 5개 결함
  - 로그인 유지·아이디 저장: 높이 19.2px
  - 아이디 찾기·비밀번호 찾기: 높이 20.8px
  - Google·GitHub: 40×40px

## 변경

- 체크박스 그룹에 `min-height: 44px`
- 찾기 링크를 `inline-flex`로 변경하고 `min-height: 44px`
- Google·GitHub 소셜 로그인 버튼을 44×44px로 확대
- 체크박스·찾기 링크·소셜 로그인에 명시적 `focus-visible` outline 추가
- 동일 평가를 로컬과 운영에서 재사용하도록 공통 Playwright 평가셋 구성

## 로컬 After

- `npm run lint`: 오류 0건
- `npm run build`: 성공, 1,828 modules
- `npx playwright test --config playwright.config.js e2e/login-accessibility.spec.js`: 5 passed, 3 의도적 skip
  - 키보드 탐색·focus: 4/4
  - mobile-360 touch target: 10/10

## 잔여 게이트

- CH-R1-03 수동 키보드·focus·touch QA는 별도 수행하며, 운영 자동화 통과만으로 완료 처리하지 않는다.

## 운영 After

- 커밋: `93c154f`
- Frontend Actions: `33199530362` 성공
- 운영 명령: `npx playwright test --config playwright.production.config.js production-e2e/login-accessibility.spec.js`
- 결과: 5 passed, 3 의도적 skip
  - 키보드 탐색·focus: 4/4 viewport
  - mobile-360 touch target: 10/10
  - Before 결함 5개 → After 결함 0개
