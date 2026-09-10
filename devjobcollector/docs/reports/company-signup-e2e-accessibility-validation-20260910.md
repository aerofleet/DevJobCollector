# DJC Company Signup P5-02 E2E·접근성 검증 보고서

> 검증일: 2026-09-10
> 범위: 개인가입, 이메일 인증, 기업 OWNER 등록, 기업 인증 요청, 접근성 및 오류 복구 UX

## 구현 결과

- Playwright API 모킹 평가셋으로 개인가입부터 이메일 인증, 기업 등록, 인증 증빙 요청까지 하나의 사용자 여정으로 검증한다.
- 회원 유형 컨트롤에 그룹명과 선택 상태를 제공하고, 가입 성공 안내를 `role="status"`로 알린다.
- 입력 레이블과 도움말을 `aria-describedby`로 분리해 비밀번호, 사업자등록번호, 증빙 문서 키의 accessible name을 고정했다.
- 필수 동의 행과 회원 유형·소셜 가입 컨트롤의 모바일 touch target을 최소 44px로 보완했다.
- 가입 API 409와 기업 조회 500을 `role="alert"`로 노출하고 기업 조회 재시도가 정상 상태로 복구되는지 검증한다.

## KPI / OKR

- **목표 KPI**: 개인→기업 핵심 E2E 4/4 viewport 통과, 접근 가능한 레이블 100%, 오류 알림·재시도 4/4 viewport 통과, 모바일 핵심 touch target 100%가 44px 이상, 전체 프론트 E2E 실패 0건.
- **OKR 연결**: 개인 계정 생성 후 기업 OWNER 등록과 인증 요청까지의 기업회원 MVP UI 연결률을 100%로 유지하고, 키보드·모바일 사용자 차단 결함을 0건으로 만든다.
- **평가셋**: Chromium 360×800, 768×1024, 1024×768, 1440×900의 4개 viewport. P5-02 16건, 전체 프론트 E2E 56건, ESLint, Vite production build를 실행했다. API는 성공·409·500 응답을 결정적으로 모킹했다.
- **Before/After**: P5-01 UI에 개인→기업 통합 E2E가 없고 비밀번호 accessible name에 도움말이 합쳐졌으며 동의 체크 영역이 16×16px였다. 보완 후 통합 여정과 오류 복구가 4개 viewport에서 통과하고 동의 행 전체가 44px 이상이다.
- **합격 기준**: P5-02 실행 실패 0건, 전체 E2E 실패 0건, 가로 overflow 0px 이하, 모바일 측정 대상 모두 44px 이상, lint/build 오류 0건.

## 검증 명령과 결과

```powershell
cd frontend
node ./node_modules/@playwright/test/cli.js test e2e/signup-company-journey.spec.js --reporter=list
# 13 passed, 3 non-mobile viewport touch 평가 의도적 skip, 실패 0

node ./node_modules/@playwright/test/cli.js test --reporter=list
# 50 passed, 6 non-mobile viewport touch 평가 의도적 skip, 실패 0

npm.cmd run lint
# 오류 0

npm.cmd run build
# 1,831 modules transformed, build 성공
```

## 남은 범위

- API 모킹 E2E이므로 운영 배포 후 실제 이메일 전달, 실제 토큰, 운영 기업 데이터 쓰기는 별도 production smoke가 필요하다.
- 증빙 파일 업로드 API는 아직 없으며 현재 UX는 비공개 Object Storage key 입력 계약을 검증한다.
- 다음 작업은 P6-01 rate limit·audit·metrics 구현이다.
