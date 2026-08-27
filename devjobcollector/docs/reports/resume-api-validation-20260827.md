# Career Hub Resume API 검증 결과

검증일: 2026-08-27~28
작업: CH-P4-01~06, CH-G1-01~02

## 결과 요약

메모리 `ConcurrentHashMap` 기반 이력서 저장소와 사용자 ID 경로 API를 제거하고, V4 `resumes` 테이블과 JWT 인증 주체를 사용하는 회원 범위 CRUD API로 교체했다.

## 변경 계약

- Base path: `/api/v1/members/me/resumes`
- 기능: 목록, 생성, 상세, 전체 수정, 상태 변경, 삭제
- 소유권: 모든 단건 작업은 `resumeId + currentMember.id` 조건
- 타 회원/미존재 ID: 동일 HTTP 404
- 본문: 기존 편집기 구조를 `content_json` JSON object로 보존
- 목록 정렬: `updatedAt DESC, id DESC`
- 상세 계약: `docs/design/resume-platform-api-contract.md`

## KPI / OKR

- 목표 KPI: 본인 CRUD 성공률 100%, 비인증 차단률 100%, 타 회원 접근 차단률 100%, 신규 평가셋 5xx 0%
- OKR 연결: Career Hub Product DoD의 실제 이력서 데이터 기능 완성
- Before/After: 프로세스 메모리 저장 및 경로 `userId` 신뢰 → MySQL Repository 및 JWT 회원 범위 조회
- 합격 기준: 신규 평가셋 failure/error/skip 0건, 전체 Gradle 회귀 성공, 프런트 E2E 8/8, 4개 viewport 가로 overflow 0px, `git diff --check` 오류 0건

## 평가셋과 결과

### 신규 평가셋

- `ResumeServiceTest`: 5/5 통과
  - 생성 및 누락 section 정규화
  - 회원 범위 목록
  - 조회·수정·상태 변경·삭제
  - 타 회원 ID 404
  - JSON object/section 타입 검증
- `ResumeControllerSecurityTest`: 3/3 통과
  - 비인증 CRUD 6경로 401
  - 인증 CRUD HTTP 계약과 생성 Location
  - 빈 제목/null content 400
- `ResumeServiceIntegrationTest`: MySQL 26.7에서 2/2 통과
  - 영속성 context 초기화와 서비스 인스턴스 재생성 후 ID·제목·본문·상태·시각 유지
  - 타 회원 조회·수정·상태 변경·삭제 4/4 HTTP 404, 원본 데이터 변경 0건

### 실행 명령

```text
gradlew.bat compileJava --no-daemon
gradlew.bat test --tests kr.itsdev.devjobcollector.career.ResumeServiceTest --tests kr.itsdev.devjobcollector.controller.ResumeControllerSecurityTest --no-daemon
gradlew.bat test --no-daemon
git -C C:\Users\aerof\spring diff --check
```

### 결과

- Java compile: `BUILD SUCCESSFUL`
- 신규 평가셋: 8/8, failures 0, errors 0, skips 0
- 전체 Gradle: `BUILD SUCCESSFUL`
- 최종 `clean test`: `BUILD SUCCESSFUL`, 10 tasks 전체 실행
- MySQL 26.7 전체 게이트: 11개 클래스, 54/54, failures 0, errors 0, skips 0
- rollback rehearsal: 이전 HEAD `070e99f`의 `CareerRepositoryIntegrationTest` 6/6 통과
- diff whitespace 오류: 0건

## MySQL 26.7 및 rollback 결과

- 공식 `mysql:26.7.0` 임시 컨테이너에서 Flyway V1→V4와 Career/인증 통합 게이트를 실행했다.
- Resume 전용 통합 평가에서 persistence context 초기화와 새 `ResumeService` 인스턴스 생성 후 데이터를 재조회했다.
- 타 회원은 조회·수정·상태 변경·삭제 전부 owner-scoped query에서 404였으며 소유자 행은 유지됐다.
- 격리 worktree의 이전 HEAD `070e99f`를 같은 V4 DB에 연결해 Repository 6/6을 통과했다. 이번 변경은 migration을 추가·수정하지 않아 애플리케이션 롤백 시 V4 데이터가 보존된다.
- 검증용 컨테이너와 임시 worktree는 결과 확인 후 제거했다.

## 미완료 게이트

- 실제 Google/GitHub 로그인, 키보드 탐색과 24시간 관찰은 수행하지 않았다.
- 운영 인증 사용자의 실제 Resume 생성·수정 여정은 수행하지 않았다.

## 운영 배포 및 회귀

- 배포 커밋: `26b4d6c` (`b66d2b3` 포함)
- Backend Actions `33105941627`: success
- Docker validation `33105941628`: success
- Frontend Actions `33105941650`: success
- 운영 health: HTTP 200, `status=UP`
- 공개 검색: HTTP 200
- 무토큰 Resume 목록: HTTP 401
- `/member`, `/my-devjobs`, `/resumes`: 3/3 HTTP 200 및 SPA 문서 반환
- `npm run e2e:production`: 운영 보호 경로 4개 × 4개 viewport, 16/16 통과
- 운영 브라우저 평가: `/member`, `/my-devjobs`, `/resumes`, `/resume` 모두 `/login?next=...`로 정확한 복귀 경로 보존
- 운영 viewport 평가: 360×800, 768×1024, 1024×768, 1440×900에서 로그인 UI 표시 및 horizontal overflow 0px
- 구분: 위 평가는 비인증 보호 경로 게이트이며, 인증 사용자의 Resume 생성·수정·재조회 CH-G1-04는 별도 미완료다.

## 프런트 연동 결과

- `/resumes`: 실제 회원별 목록, 상태, 수정 시각, Loading·Empty·Error·Success·Unauthorized 상태
- `/resume`: 신규 모드와 `?resumeId={id}` 수정 모드, 제목·본문 저장 후 `/resumes` 복귀
- `npm run lint`: ESLint 오류 0건
- `npm run build`: 성공, 1,828 modules transformed
- `npm run e2e`: 8/8 통과, 2개 이력서 여정 × 360/768/1024/1440px
- viewport 평가 결과: 4개 환경 모두 `documentElement.scrollWidth - innerWidth = 0px`
- Before/After: 모바일·태블릿에서 저장 바와 64px 탭바가 겹쳐 클릭 4/8 실패 → 저장 바 위치 및 본문 여백 보정 후 8/8 통과
