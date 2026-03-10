# 🚀 DevJobCollector

공공데이터포털(알리오) Open API를 활용해 공공기관 채용 정보를 자동 수집·정제하여 DB화하고, REST API로 제공하는 백엔드 서비스입니다.  
프론트엔드 대시보드는 React + Vite 기반으로 구성되어 있습니다.

[![Notion](https://img.shields.io/badge/Project_Documentation-000000?style=for-the-badge&logo=notion&logoColor=white)](https://www.notion.so/DJC-Project-2d89cc8ccb4a8094a818f92fcc4fd8d4?source=copy_link)

---

## 🛠 Tech Stack

- **Backend**: Java 21, Spring Boot 3.5.11, Spring Data JPA, Querydsl, Spring AOP, Actuator
- **Data**: MySQL 9.6
- **Build Tool**: Gradle
- **External API**: Public Data Portal (ALIO) Recruitment API
- **Frontend**: React 19, Vite, Tailwind CSS, Bootstrap
- **Deploy (Frontend)**: Cloudflare Wrangler

---

## ✨ Key Features (Current)

### 1) 데이터 수집 파이프라인
- **목록 → 상세 연쇄 수집**으로 공고 본문, 지원 자격, 전형 절차까지 적재
- **일괄 중복 체크**로 수집 성능 개선 (`SourcePlatform + OriginalSN`)
- **스케줄러 기반 자동 수집**: 매일 오전 10시 / 오후 4시 실행
- **앱 기동 시 초기 수집**: 서비스 시작 시 1회 수집 (테스트 목적)

### 2) 데이터 모델링 & 정규화
- 공고 중심 모델링 + **기술스택(TechStack)**, **첨부파일(JobFile)** 분리 관리
- **동시성 안전한 기술스택 생성** 로직으로 중복 삽입 방지

### 3) 검색 & 필터링
- **통합 검색**: 제목/기관/지역/경력/직무/기술스택 키워드 검색
- **기술 스택 필터링** 및 **활성 공고** 조회
- **마감일 기준 정렬(D-Day 우선)** + 최신 등록 보조 정렬

### 4) 유지보수/운영 자동화
- **만료 공고 자동 비활성화** (매일 00:05)
- **1년 이상 비활성 공고 정리** (매일 00:30, 로그 백업 후 삭제)
- **요청/메서드 성능 로깅** (Interceptor + AOP)

### 5) 이력서 CRUD API
- 이력서 조회/등록/수정 API 제공 (`/api/v1/resume`)

---

## 🚦 Getting Started

### Prerequisites
- JDK 21
- MySQL 9.6+
- Node.js (Frontend 실행 시)

### Backend Setup

1. 저장소 이동
2. 설정 파일 수정  
   - `devjobcollector/src/main/resources/application.yml`
   - DB 접속 정보는 `application-local.yml` 또는 환경 변수로 분리 권장
3. 필수 환경 변수 설정  
   - `API_SERVICE_KEY`: 공공데이터포털 서비스 키
4. 실행

```bash
cd devjobcollector
./gradlew bootRun
```

### Frontend Setup (Optional)

```bash
cd devjobcollector/frontend
npm install
npm run dev
```

---

## 🔌 API Endpoints

### Jobs
- `GET /api/v1/jobs` 공고 목록 (페이징)
- `GET /api/v1/jobs/{id}` 공고 상세
- `GET /api/v1/jobs/search` 통합 검색  
  - 예: `?keyword=개발자&location=서울&experience=신입&page=0&size=20`
- `GET /api/v1/jobs/active` 활성 공고
- `GET /api/v1/jobs/tech-stack?stackName=Java` 기술 스택 필터링

### Resume
- `GET /api/v1/resume/{userId}` 이력서 조회
- `POST /api/v1/resume` 이력서 저장
- `PUT /api/v1/resume/{id}` 이력서 수정

---

## 🗂️ 프로젝트 구조 (Backend)

```
devjobcollector/src/main/java/kr/itsdev/devjobcollector
├── config
├── controller
├── domain
├── dto
├── monitoring
├── repository
├── service
└── DevjobcollectorApplication.java
```

---

## 🔍 Trouble Shooting & Lessons Learned

| 문제 상황 | 원인 | 해결 방안 |
| --- | --- | --- |
| JSON Deserialization Error | 응답이 List/Object로 동적 변화 | 상세 전용 DTO 분리로 타입 안정성 확보 |
| Type Mismatch (SN) | 숫자형 고유번호가 문자열로 유입 | 식별자 도메인을 String으로 통일 |
| Port Conflict (8080) | 이전 프로세스 비정상 종료 | `taskkill` 로 포트 점유 프로세스 종료 |

---

## 📈 Future Roadmap

- [ ] 커리어(Career) API 연동 확장 및 통합 수집
- [ ] 알림(Email/Slack) 기반 수집 실패 감지 및 리포트
- [ ] 프론트 대시보드 고도화 (필터/통계/차트)

---

## 📚 Documentation

프로젝트의 상세한 개발 일지, 트러블슈팅 및 기술적 의사결정 과정은 Notion에서 확인할 수 있습니다.

