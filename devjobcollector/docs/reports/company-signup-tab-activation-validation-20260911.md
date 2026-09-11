# DJC 기업회원 가입 탭 활성화 검증 보고서

> 검증일: 2026-09-11
> 범위: 회원가입 화면의 기업회원 선택, 이메일·소셜 인증 후 기업 등록 연결

## 원인과 조치

- 기존 기업회원 버튼은 선택 상태를 변경하지 않고 인증 필수 `/company`로 즉시 이동했다. 비로그인 사용자는 로그인 화면으로 전환되어 회원가입 탭이 동작하지 않는 UX였다.
- 기업회원 버튼을 실제 선택 탭으로 변경하고 `active` 및 `aria-pressed` 상태를 개인회원 탭과 상호 배타적으로 연결했다.
- 기업회원 선택 시 공통 담당자 계정을 먼저 생성한다는 안내와 `가입 후 기업 등록하기` CTA를 표시한다.
- 이메일 인증 완료 후 `/company`로 이동하며, 기존 사용자의 로그인 링크도 `/login?next=/company`를 유지한다.
- Google/GitHub 가입은 `postLoginNextPath=/company`를 보존하고 callback 완료 후 기업 등록 화면으로 이동한다.
- React 개발 모드의 effect 재실행이 callback 목적지를 `/member`로 덮어쓰지 않도록 토큰 callback을 한 번만 처리한다.

## KPI / OKR

- **목표 KPI**: 기업회원 탭 선택 상태 4/4 viewport 반영, 이메일 인증 후 `/company` 이동 4/4, 소셜 callback 후 `/company` 이동 4/4, 전체 프론트 E2E 실패 0건.
- **OKR 연결**: 신규 기업 담당자가 회원가입 화면에서 기업 OWNER 등록 화면까지 중단 없이 도달하는 UI 연결률을 100%로 만든다.
- **평가셋**: Chromium 360×800, 768×1024, 1024×768, 1440×900. 기업회원 선택·이메일 가입·소셜 callback 8건과 전체 프론트 E2E 60건, ESLint, Vite production build를 실행했다.
- **Before/After**: 기업회원 선택 후 `/login?next=/company`로 즉시 이탈하고 탭 상태 변화 0/4 → 회원가입 화면 유지 및 선택 상태 4/4, 이메일·소셜 인증 후 `/company` 도달 8/8.
- **합격 기준**: 기업가입 핵심 8/8, 전체 E2E failure 0, 비모바일 touch 전용 평가 외 skip 0, lint/build 오류 0건.

## 검증 명령과 결과

```powershell
# 터미널 1
node ./node_modules/vite/bin/vite.js --mode e2e --host 127.0.0.1 --port 4174

# 터미널 2
$env:DJC_E2E_EXTERNAL_SERVER='true'
node ./node_modules/@playwright/test/cli.js test --workers=2
# 54 passed, 6 non-mobile touch 평가 의도적 skip, 실패 0

npm.cmd run lint
# 오류 0

npm.cmd run build
# 1,831 modules transformed, build 성공
```

## 운영 반영

- `main` push: `77d242e..5c0f473`
- Frontend Actions `34592049096`: 성공, Cloudflare Worker version `2f7143a2-7d1a-4a38-a42b-0dfcf96501c9`
- Backend Actions `34592049084`: 성공
- Docker validation `34592049116`: 성공
- 운영 번들 `index-C2xtIVPV.js`: `기업회원 가입은 준비 중입니다.` 0건, `가입 후 기업 등록하기` 1건 이상, 기업가입 안내 문구 1건 이상 확인

## 남은 범위

- E2E는 결정적 API/OAuth callback 모킹 평가다. 실제 Provider 계정 승인과 운영 기업 데이터 쓰기 smoke는 별도로 수행해야 한다.
- 기업 계정은 별도 `users.account_type`이 아니라 공통 사용자 계정과 `company_members` OWNER 관계로 판정한다.
