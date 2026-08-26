# CH-P3-04~05 마이데브잡 실제 데이터 연동 검증

## 결과

`/my-devjobs`의 저장 공고·지원 현황·최근 본 공고 세 탭을 운영 API와 연결하고 Cloudflare에 배포했다.

- 세 API를 병렬 조회해 실제 건수와 회원 소유 목록을 표시한다.
- 저장 공고 삭제와 지원 상태 변경 결과를 즉시 화면에 반영한다.
- 최근 본 공고는 최종 조회일과 누적 조회 횟수를 표시한다.
- 공고 상세 진입은 로그인 회원의 최근 조회를 기록한다.
- 상세 화면에서 공고 저장과 지원 기록 생성 후 원문 지원 페이지로 이동할 수 있다.
- 비로그인 사용자는 상세 공개 열람을 유지하며 회원 활동 버튼을 누를 때 원래 경로를 보존해 로그인으로 이동한다.
- Loading, Empty, Error+Retry, Success, Unauthorized 상태를 분리했다.

## KPI / OKR

- 목표 KPI: 활동 API 연동률 3/3, 상태 UI 분리 5/5, 비인증 API 차단률 100%, ESLint 오류 0건, production build 성공률 100%, 운영 route 성공률 2/2.
- OKR 연결: Career Hub KR1의 회원 기능 도달률 100%와 KR2의 북마크·지원·최근 조회 데이터 유실 0건에 기여한다.

## 평가셋과 결과

| 평가셋 | 합격 기준 | 결과 |
|---|---|---|
| 활동 조회 API | 북마크·지원·최근 조회 3/3 연결 | 3/3 |
| 사용자 변경 동작 | 북마크 삭제·지원 상태 변경·상세 활동 생성 | 구현 완료 |
| 상태 UI | Loading/Empty/Error/Success/Unauthorized 5/5 | 5/5 |
| ESLint | 오류 0건 | 오류 0건 |
| Vite production build | 종료 코드 0 | 1,828 modules, 성공 |
| Cloudflare Actions | build/deploy 성공 | `32971481254` 성공 |
| 운영 SPA route | `/my-devjobs`, `/job/1` HTTP 200 | 2/2 |
| 운영 무토큰 API | 활동 API 3종 모두 HTTP 401 | 3/3 |

## Before / After

| 항목 | Before | After |
|---|---|---|
| 활동 수량 | 세 탭 모두 고정 0 | API 응답 기반 실제 수량 |
| 활동 목록 | 빈 상태만 표시 | 회원별 공고·지원 상태·조회 횟수 표시 |
| 상세 활동 | 원문 지원 링크만 제공 | 최근 조회·저장·지원 기록 연결 |
| 오류 복구 | 없음 | 오류 안내와 재시도 버튼 |
| 비인증 회원 행동 | 기록 기능 없음 | 원래 상세 경로 보존 후 로그인 이동 |

## 검증 명령

```powershell
npm run lint
npm run build
gh run watch 32971481254 --repo aerofleet/DevJobCollector --exit-status
curl.exe -sS -o NUL -w "%{http_code}" https://<FRONTEND_DOMAIN>/my-devjobs
curl.exe -sS -o NUL -w "%{http_code}" https://<FRONTEND_DOMAIN>/job/1
curl.exe -sS -o NUL -w "%{http_code}" https://<API_DOMAIN>/api/v1/members/me/bookmarks
```

## 합격 판단과 잔여 항목

- CH-P3-04~05 정적 검증·배포·route·무토큰 보안 기준은 합격이다.
- 실제 로그인 계정으로 저장→목록→삭제, 지원→상태 변경, 상세→최근 조회 여정은 운영 수동 QA로 남긴다.
- 360/768/1024/1440px 시각·키보드 QA는 CH-R1-02~03 미완료 상태를 유지한다.
- Actions의 Node 20 deprecation annotation은 별도 워크플로 유지보수 항목이다.
- 다음 독립 작업은 CH-P3-06 동시 요청·타 회원 데이터 접근 차단 테스트다.
