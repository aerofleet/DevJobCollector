# DJC Company Frontend P5-01 검증 보고서

> 검증일: 2026-09-10
> 범위: 로그인 사용자의 기업 등록, 기업·membership·최신 인증 상태 조회, 인증 요청 UI

## 구현 결과

- `GET /api/v1/companies/me`를 추가해 로그인 사용자의 `LEFT`가 아닌 기업 소속과 최신 인증 요청 상태를 조회한다.
- 응답은 마스킹된 사업자번호만 반환하며 사업자번호 hash, 증빙 object key, 반려 사유는 노출하지 않는다.
- `/company`에 기업 미등록, 인증 요청 가능, 검토 중, 승인, 반려, 정지, 종료 상태 UI를 추가했다.
- 공통 계정 원칙을 유지해 기업 등록은 인증된 개인 계정에서 수행하며 `users.account_type`을 추가하지 않았다.
- 회원가입 탭, 헤더, Career Hub 사이드바와 홈에서 기업센터로 진입할 수 있다.

## KPI / OKR

- **목표 KPI**: 기업 상태 조회 인증 계약 2/2 통과, 민감 필드 응답 노출 0건, P5-01 타깃 테스트 실패·오류·skip 0건, 프론트 lint/build 오류 0건.
- **OKR 연결**: 기업 담당자가 등록과 인증 진행 상태를 한 화면에서 확인하게 해 기업회원 MVP 핵심 흐름의 UI 연결률을 0%에서 100%로 높인다.
- **평가셋**: 상태 조회 Service 정상·빈 목록 2건, Company Controller 인증·응답 계약 포함 12건, 전체 Gradle 450건, Vite ESLint와 production build.
- **Before/After**: 기업회원 탭 비활성·상태 조회 API 없음 → `/company` 진입, 기업 등록, 새로고침 가능한 상태 조회, 인증 요청과 최근 심사 상태 표시.
- **합격 기준**: 타깃 14/14, 전체 Gradle 450건 중 실패·오류 0, lint/build 오류 0, 원문 사업자번호·hash·증빙 key 응답 노출 0건.

## 검증 명령과 결과

```powershell
.\gradlew.bat test --tests "kr.itsdev.devjobcollector.company.CompanyProfileServiceTest" --tests "kr.itsdev.devjobcollector.controller.CompanyControllerSecurityTest"
# 14/14, failures/errors/skipped 0

.\gradlew.bat test
# 450건, failures/errors 0, 환경변수 기반 78건 의도적 skip, BUILD SUCCESSFUL

cd frontend
npm.cmd run lint
# 오류 0

npm.cmd run build
# 1,831 modules transformed, build 성공
```

## 남은 범위

- P5-02에서 개인·기업 가입 E2E, 키보드·스크린리더 명칭, 모바일 viewport와 API 오류 상태를 검증한다.
- 현재 인증 증빙 업로드 API는 없으므로 MVP UI는 Object Storage가 발급한 비공개 object key 입력 계약을 그대로 사용한다. 실제 파일 업로드 UX는 별도 capability로 연결해야 한다.
- 인앱 브라우저 자동 QA는 브라우저 연결 메타데이터 오류로 실행하지 못했으며 P5-02의 수동/자동 E2E 게이트에서 보완한다.
- 운영 배포 변경은 없다.
