# 관리자 상세 모달 전환 및 배포 검증 (2026-09-30)

## 범위와 목표

- **OKR 연결**: 운영자가 회원·기업·공고·관리자 상세를 목록 맥락에서 확인하여 관리자 BO 업무를 한 화면에서 처리한다.
- **목표 KPI**: 네 화면의 `상세` 버튼 4/4가 하단 패널 대신 모달을 연다. 360/768/1024/1440px에서 대상 모달 E2E 실패 0건, 관리자 4개 직접 경로 HTTP 200을 합격 기준으로 둔다.
- **평가셋**: 회원, 기업, 공고, 관리자 목록의 `상세` 버튼 4개와 모달 닫기(닫기 버튼, Escape, 배경 클릭), 포커스 복귀 및 본문 스크롤 잠금. Playwright 4개 viewport와 배포 URL의 `/users`, `/companies`, `/jobs`, `/admins`를 확인한다.
- **Before/After**: 변경 전 상세 4/4는 목록 아래 인라인 패널에 표시됐다. 변경 후 상세 4/4는 공통 `DetailModal`에 표시된다.
- **합격 기준**: 대상 모달 E2E 실패 0건, lint/build 통과, 운영 배포 성공, 네 직접 경로 HTTP 200.

## 구현

- `admin-frontend/src/components/DetailModal.jsx`를 회원·기업·공고·관리자 상세에 공통 적용했다.
- 모달은 Escape·배경 클릭·닫기 버튼으로 닫히고, 닫은 뒤 기존 `상세` 버튼으로 포커스가 돌아간다. 열려 있는 동안 본문 스크롤을 잠그고 모달 내부 포커스를 유지한다.
- 관리자 계정 생성 결과의 일회성 비밀값 표시 영역은 생성 흐름 그대로 유지했다.

## 검증 결과

- `npm.cmd run lint`: 통과.
- `npm.cmd run build`: 통과.
- `ADMIN_E2E_EXTERNAL_SERVER=true npm.cmd run e2e`: 29 passed, 3 skipped. 평가 viewport는 360/768/1024/1440px. 기업 상세 모달의 Escape·배경 클릭·포커스 동작 대상 E2E는 4 passed.
- GitHub Actions `admin frontend deploy` 실행 `36605980409`: 성공. 배포 커밋 `046369f`.
- 운영 smoke: `/users`, `/companies`, `/jobs`, `/admins` 각각 HTTP 200. 배포 JS 자산 `/assets/index-EyqBPEM3.js` HTTP 200.

## 남은 검증

- 전체 관리자 SPA의 접근성 감사와 모든 상태 변경 확인 흐름의 최종 검증은 별도 체크리스트 항목으로 유지한다.
- 위 E2E 수치는 인증된 외부 서버 대상 테스트 결과다. 운영에서 실계정으로 네 상세를 클릭하는 역할별 QA는 P4에서 진행한다.
