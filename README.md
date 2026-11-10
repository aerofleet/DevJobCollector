# DevJobCollector (DevJobs)

공공기관과 기업 채용 페이지의 공고를 수집하고 개발 직무 탐색과 구직 활동을 지원하는 서비스입니다. Spring Boot API, React 사용자 앱과 관리자 앱으로 구성됩니다.

- 서비스: [djc.itsdev.kr](https://djc.itsdev.kr)
- 프로젝트 문서: [Notion](https://www.notion.so/DJC-Project-2d89cc8ccb4a8094a818f92fcc4fd8d4)

## 주요 기능

- **공고 수집**: ALIO 공공기관 공고와 Greenhouse, Lever, Greeting 등 기업 채용 원본을 수집합니다. 백패커 등 대상 기업은 `application.yml`의 수집 설정으로 관리합니다. 사람인 등 일부 수집기는 별도 활성화와 자격증명이 필요합니다.
- **검색과 필터**: 검색어, 회사, 지역, 경력, 직무와 기술 스택으로 공개 중인 공고를 찾습니다. 회사 선택지는 활성·공개·미마감 공고를 기준으로 조회하며 필터를 다시 열 때 갱신합니다.
- **공고 상세**: 지원 자격과 업무, 전형 정보 및 첨부파일을 표시합니다. 산업기능요원 관련 채용은 별도로 표시합니다.
- **첨부 미리보기**: 지원되는 ALIO HWP/HWPX 첨부를 브라우저에서 미리 볼 수 있습니다. 미리보기가 실패하거나 서식이 다르면 원본 파일을 열 수 있습니다. 프록시는 허용된 원본과 공개 공고를 확인하고 파일 크기 및 동시 요청을 제한합니다. [검증 범위와 제한](devjobcollector/docs/hwp-preview-poc.md)
- **회원 및 커리어**: 회원 인증, 이력서, 저장한 공고·지원 기록, 기업회원의 공고 관리 기능을 제공합니다. 이력서 분석과 추천 등 AI 기능은 관련 설정을 활성화한 환경에서 사용할 수 있습니다.

## 기술 구성

| 영역 | 구성 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 3.5.11, Spring MVC/WebClient, Spring Data JPA, Querydsl, Spring Security |
| 데이터 | MySQL, Flyway 마이그레이션. 마이그레이션 검증은 MySQL 26.7.0 이미지 사용 |
| 사용자·관리자 앱 | React 19, Vite, React Router. 사용자 앱의 HWP 렌더링은 `@rhwp/core` 사용 |
| 배포 | GitHub Actions, GHCR·Docker Compose, Cloudflare Workers |

## 저장소 구조

```text
.
├── .github/workflows/            # CI와 배포 워크플로
└── devjobcollector/
    ├── src/                      # Spring Boot API, 수집기, 테스트, Flyway 마이그레이션
    ├── auth-common/              # 공통 인증 모듈
    ├── frontend/                 # 사용자 앱과 Cloudflare Worker
    ├── admin-frontend/           # 관리자 앱
    ├── ops/                      # Docker 배포와 DB 마이그레이션 검증
    ├── docs/                     # 설계, 운영, 기능 검증 문서
    ├── Dockerfile
    └── compose.yaml              # 백엔드 컨테이너 구성
```

## 로컬 실행

JDK 21, Node.js 22, npm과 MySQL이 필요합니다. Docker는 컨테이너 실행이나 MySQL 통합 검증에 사용합니다. `compose.yaml`은 **백엔드 앱만** 정의하므로 접근 가능한 MySQL을 별도로 준비해야 합니다.

### 백엔드

`devjob` 데이터베이스와 계정을 준비한 뒤 환경 변수를 설정합니다. 아래 값은 예시이며 실제 자격증명은 저장소에 커밋하지 않습니다. 로컬에서 외부 수집을 실행하지 않으려면 수집기 플래그를 끕니다.

```bash
cd devjobcollector
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/devjob?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true'
export SPRING_DATASOURCE_USERNAME='<DB_USER>'
export SPRING_DATASOURCE_PASSWORD='<DB_PASSWORD>'
export API_SERVICE_KEY='<API_SERVICE_KEY>'
export PUBLIC_DATA_COLLECTION_ENABLED=false
export ATS_COLLECTION_ENABLED=false
export SARAMIN_COLLECTION_ENABLED=false
export TOSS_COLLECTION_ENABLED=false
export NAVER_COLLECTION_ENABLED=false
./gradlew bootRun
```

Windows에서는 환경 변수를 PowerShell의 `$env:이름`으로 설정하고 `./gradlew.bat bootRun`을 사용합니다. Flyway 접속 계정을 분리하려면 `FLYWAY_USERNAME`, `FLYWAY_PASSWORD`를 설정합니다. OAuth·메일·AI 등 선택 기능과 Docker 환경 변수는 [환경 변수 예시](devjobcollector/.env.docker.example) 및 `application.yml`을 참고하세요.

### 사용자 앱

```bash
cd devjobcollector/frontend
npm ci
npm run dev
```

API 기본 주소는 `http://localhost:8080/api/v1`이며, 다른 서버를 사용하면 `VITE_API_BASE_URL`을 설정합니다. 관리자 앱은 `devjobcollector/admin-frontend`에서 `npm ci`, `npm run dev`로 실행합니다.

## 주요 API

| 메서드와 경로 | 설명 |
| --- | --- |
| `GET /api/v1/jobs` | 공고 목록 |
| `GET /api/v1/jobs/{id}` | 공고 상세 |
| `GET /api/v1/jobs/search` | 검색 및 필터. 예: `keyword`, `location`, `experience`, `jobCategory`, `techStack`, 반복 가능한 `company`, `page`, `size` |
| `GET /api/v1/jobs/companies` | 현재 공개 공고의 회사 목록. 선택적으로 `query` 사용 |
| `GET /api/v1/job-files/{fileId}/preview` | 지원되는 공개 공고 첨부의 미리보기 파일 |
| `GET/POST /api/v1/members/me/resumes` | 로그인 회원의 이력서 목록·생성 |
| `GET /actuator/health` | 서비스 상태 |

회원·이력서·기업 관리 API에는 인증 및 해당 권한이 필요합니다. 실제 요청·응답과 추가 API는 각 컨트롤러를 참고하세요.

## 검증

```bash
cd devjobcollector
./gradlew test
# Docker와 Bash가 있는 환경에서 MySQL 마이그레이션 통합 검증
bash ops/db/run-member-migration-tests.sh

cd frontend
npm ci
npm run lint
npm run build
# 최초 한 번: npx playwright install chromium
npx playwright test e2e/job-filter-catalog.spec.js
```

일부 DB 통합 테스트는 별도 MySQL 환경 변수가 필요합니다. Playwright 기본 설정은 모바일부터 데스크톱까지 네 화면 크기를 사용합니다. HWP 표본 검증은 [별도 검증 문서](devjobcollector/docs/hwp-preview-poc.md)를 참고하세요.

## 배포

- 백엔드: `main`의 백엔드 관련 경로 변경 시 [백엔드 워크플로](.github/workflows/djc-backend-deploy.yml)가 테스트·마이그레이션 검증 후 GHCR 이미지를 빌드하고 OCI에 Docker Compose로 배포합니다.
- 사용자 앱: `main`의 프런트엔드 관련 경로 변경 시 [프런트엔드 워크플로](.github/workflows/djc-frontend-deploy.yml)가 빌드 후 Cloudflare Workers에 배포합니다.
- 관리자 앱은 [별도 워크플로](.github/workflows/djc-admin-frontend-deploy.yml)로 배포합니다. 각 워크플로는 수동 실행도 지원합니다.
- README만 변경하면 위 경로 기반 자동 배포는 실행되지 않습니다. 환경 변수와 비밀값은 GitHub 설정 및 운영 환경에서 관리합니다.
