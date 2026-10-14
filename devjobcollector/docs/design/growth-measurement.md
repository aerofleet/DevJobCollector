# 검색 유입·첫 방문·활성화 측정 계약

## 목표와 합격 기준

- OKR 연결: 개발자 공고 탐색 진입과 재방문을 관측하고 채널별 모집 실험의 기준값을 확보한다. 회원가입·활성화율 개선은 실제 데이터가 쌓이기 전에는 성과로 주장하지 않는다.
- SEO KPI: robots는 text/plain, 사이트맵은 유효 XML, 공개 공고별 canonical/OG와 비공개 경로 noindex 평가 9/9 통과.
- 첫 방문 KPI: JS 다운로드를 막아도 제목·검색창 표시, hydration 시 기존 제목 DOM 유지, 360/1440px 가로 넘침·hydration 오류 0건. 실사용 목표는 모바일/데스크톱 p75 LCP ≤ 2.5초, INP ≤ 200ms, CLS ≤ 0.1이며 Cloudflare 실사용 데이터로 별도 판정한다.
- 측정 KPI: 수집 동의 전 전송·식별자 생성 0건, 원문 검색어·이메일·인증 토큰 수집 0건, 같은 eventId 재전송 시 최종 DB 1행, 비관리자 집계 API 접근 0건.
- 평가셋: Worker 9건, 분석 브라우저 7개 시나리오 × 4 viewport, 빌드 결과 2개 × 2 viewport, 관리자 패널 4 viewport, Spring 보안 6건, 격리 MySQL 중복·활성화·D7·보존 4건. 공개 API·검색·세션 회귀도 수행한다.
- Before/After: robots/sitemap이 홈 HTML을 반환 → 별도 text/XML 응답; 빈 root → 사전 렌더링 홈; 기업 직접 공고 VIEW/APPLY_CLICK만 집계 → 전체 공고의 선택 동의 기반 8개 행동 집계. 첫 방문 성능은 동일 측정 스크립트의 전후 JSON으로 비교하며 소수 표본을 p75/p95로 표현하지 않는다.

## 검색과 공유

- Worker가 `/`, `/jobs`, `/job/:id`, 법적 고지 및 회원 경로에 canonical/OG/Twitter/robots를 설정한다. 쿼리·인증 토큰은 canonical에 포함하지 않는다.
- `/sitemap.xml`은 정적 페이지 사이트맵과 활성 공고 500개 단위 사이트맵의 인덱스다. 공고 ID 오름차순, 검색 API의 공개 범위와 동일하다. 갱신 중 페이지 경계가 바뀔 수 있으므로 검색엔진의 재수집으로 수렴한다.
- 비공개·삭제 공고는 공개 API의 404를 전달한다. 원본 API 장애는 503 + Retry-After로 전달해 빈 성공 사이트맵으로 오인하지 않게 한다.
- 공유는 native share 또는 clipboard 복사이며 공고의 정규 URL만 사용한다. 공유 취소·실패는 성공 이벤트로 세지 않는다.
- 공유 이미지는 공통 브랜드 이미지이며 공고명·기업명은 개별 OG 제목에 담는다. 공고 원문 전체 SSR과 JobPosting 구조화 데이터는 이번 범위에 포함하지 않는다.
- 첫 화면의 기본 CSS는 HTML에 포함해 별도의 렌더링 차단 요청을 없앤다. 홈은 기존 DOM을 hydration하며, 저장된 로그인·통계 동의 상태는 서버의 공통 초기 상태 이후 동기화한다.

## 선택적 수집과 보존

- 화면 하단 `이용 통계 설정`에서 명시적으로 허용한 브라우저만 자체 분석 이벤트를 전송한다. 기본값은 미동의다. 이는 기존 Cloudflare의 별도 성능 계측 설정을 바꾸지 않는다.
- DNT/GPC를 우선한다. 로그인 계정과 결합하지 않는 무작위 브라우저 UUID는 생성 후 90일에 교체한다. 세션은 sessionStorage에 저장하고 30분 무활동 후 교체한다.
- 동의 철회 시 이후 전송 중단 및 기기의 통계 ID 삭제. 기존 서버 기록은 90일 보존 후 UTC 일별 삭제 작업으로 정리한다.
- 수집 필드: eventId, visitorId, sessionId, kind, 페이지 종류(enum), 공고 번호, 제한된 source/medium/campaign, 서버 UTC 시각. 원문 URL/검색어/이메일/인증 토큰/계정 ID/IP를 이벤트 테이블에 저장하지 않는다.
- 수집 실패는 서비스 동작을 막지 않는다. 네트워크/5xx만 동일 eventId로 1회 재시도한다. 브라우저 차단·오프라인·동의 표본 편향으로 전체 이용자를 대표한다고 보장하지 않는다.
- API는 인스턴스당 분당 전체 6,000회·브라우저당 120회 제한. 클라이언트가 보낸 행동 신호이므로 봇·조작을 완전히 배제한 매출/정산 근거로 사용하지 않는다.

## 행동과 지표

| 이벤트 | 발생 조건 |
|---|---|
| PAGE_VIEW | 동의 후 경로/쿼리 전환. StrictMode 중복 제외 |
| SEARCH | 검색 결과 경로 진입. 최초 기본 목록 진입도 포함하며 원문 검색어는 미수집 |
| JOB_VIEW | 공고 상세 응답 성공 |
| BOOKMARK | 공고 저장 API 성공. 재저장도 이벤트가 될 수 있어 활성화는 고유 세션으로 계산 |
| APPLY_CLICK | 로그인된 사용자의 지원 버튼 클릭. 실제 지원 완료는 아님 |
| SIGNUP | 이메일 인증 가입 API 성공. 소셜 신규 가입은 LOGIN에 포함 |
| LOGIN | 이메일 로그인 성공 또는 소셜 callback 완료 |
| SHARE | native share 완료 또는 clipboard 복사 성공 |

- 관리자 대시보드 `/api/v1/admin/dashboard/growth`: 관리자 세션 필요. 일반 회원 JWT·비인증 접근은 401.
- 최근 28일 행동별 고유 브라우저·고유 세션·이벤트 수. 단계 집계는 순서가 강제된 전환 퍼널이 아니다.
- 채널별 활성화율 = BOOKMARK 또는 APPLY_CLICK이 있는 고유 세션 / 해당 채널의 이벤트가 있는 고유 세션. 세션 시작 시 source/medium/campaign을 고정한다.
- D7 = 최근 90일 내 최초 PAGE_VIEW 날짜로부터 정확히 7일째 PAGE_VIEW가 있는 브라우저 / 관찰이 끝난 브라우저. 기준 UTC, 최초 방문일이 오늘-35일 이상·오늘-7일 미만인 28개 일별 코호트만 포함한다. 분모 0은 0%가 아닌 N/A. 새 배포의 D7 실측에는 최소 8일이 필요하다.
- 기기 변경·브라우저 저장소 삭제·식별자 교체는 다른 방문자다. 방문자 수는 사람 수가 아니다.

## 캠페인 사용

예: `https://<FRONTEND_DOMAIN>/?utm_source=velog&utm_medium=community&utm_campaign=launch_202610`.
값은 소문자 영문·숫자·밑줄·하이픈 48자 이내이며 개인 정보는 넣지 않는다. 알려진 referrer는 google/naver/github/velog/okky로 분류하고 그 밖에는 referral/direct로 묶는다.

전환율 기준값 확보 전 임의의 개선율을 주장하지 않는다. 첫 2주 동의 표본의 채널별 세션·활성화 분모와 D7 성숙 코호트를 보고, 다음 모집 실험의 목표·표본 수·기간을 정한다.

## 검증 명령과 운영 확인

```text
frontend: npm run lint && npm run build
frontend: node --test worker/seo.test.mjs
frontend: npx playwright test e2e/growth.spec.js
frontend: npx playwright test --config playwright.built.config.js
admin-frontend: npm run lint && npm run build
admin-frontend: npx playwright test e2e/growth-panel.spec.js
backend: ./gradlew test --tests 'kr.itsdev.devjobcollector.growth.*'
```

MySQL 통합 테스트에는 격리 DB의 DJC_MIGRATION_TEST_URL/USERNAME/PASSWORD가 필요하다. `DJC growth validation` workflow가 MySQL 26.7에서 Flyway V13과 집계를 검증한다. 운영에서는 robots/sitemap content-type, 공고 raw HTML 메타데이터, 비인증 통계 401, 관리자 패널을 확인한다. 테스트 이벤트를 운영에 전송할 때는 캠페인 `verification`으로 구분한다.
