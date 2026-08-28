# Career Hub 운영 인증 E2E 준비 결과

## 범위

- 대상: CH-G1-04 운영 인증 사용자의 Resume 핵심 여정
- 경로: `/resume` → `/resumes` → `/resume?resumeId=<ID>` → `/resumes`
- 인증: `DJC_E2E_ACCESS_TOKEN` 환경변수를 브라우저 `localStorage`에 주입
- 보안: 토큰을 코드·출력에 기록하지 않고 인증 테스트의 Playwright trace를 비활성화

## KPI / OKR / 평가셋

- 목표 KPI: 생성·수정·재조회·정리 4단계 성공률 100%, 운영 테스트 데이터 잔존 0건
- OKR 연결: Career Hub Product DoD의 운영 인증 핵심 여정 검증 완료
- 평가셋: 운영 `desktop-1440` 단일 viewport, 인증 사용자 1명, 고유 제목 이력서 1건
- Before/After: 인증 운영 스펙 0건 → 토큰 조건부 스펙 1건과 전용 실행 명령 1개
- 합격 기준: POST 201, PUT 200, 수정 제목 재조회 성공, DELETE 200/204, 삭제 후 목록 0건

## 구현

- `frontend/production-e2e/authenticated-career-hub.spec.js`
  - 고유 제목으로 이력서 생성
  - 목록에서 생성 결과 확인
  - 이력서 제목 수정 후 목록 복귀
  - 새로고침 후 수정 결과 재조회
  - API로 테스트 이력서 삭제 후 목록에서 제거 확인
  - 중간 실패 시 `finally`에서 정리 재시도 및 정리 응답 검증
- `npm run e2e:production:authenticated`
  - 운영 쓰기를 `desktop-1440` 프로젝트 1회로 제한
  - 토큰 미설정 시 명시적으로 skip

## 검증 결과

- `npm run lint`: 성공, 오류 0건
- `npm run build`: 성공, 1,828 modules
- `npm run e2e:production`: 기존 비인증 보호 경로 16/16 성공
- `npm run e2e:production:authenticated`: 토큰 미설정 조건에서 1건 안전 skip

## 미완료 조건

CH-G1-04는 아직 완료가 아니다. 운영 전용 인증 토큰을 현재 셸의 `DJC_E2E_ACCESS_TOKEN`에 설정하고 아래 명령이 1/1 통과해야 완료로 전환한다.

```powershell
$env:DJC_E2E_ACCESS_TOKEN = '<EPHEMERAL_TEST_TOKEN>'
npm run e2e:production:authenticated
Remove-Item Env:DJC_E2E_ACCESS_TOKEN
```

토큰 원문은 명령 기록, 문서, GitHub Actions 로그에 남기지 않는다.
