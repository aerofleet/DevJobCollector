# 🚀 DevJobCollector: 공공데이터 기반 채용 정보 수집 엔진

**DevJobCollector**는 공공데이터포털(알리오)의 Open API를 활용하여 공공기관의 신규 채용 정보를 자동으로 수집, 정제하여 데이터베이스화하고 이를 사용자에게 REST API로 제공하는 백엔드 서비스이다.

---

## 🛠 Tech Stack

-   **Backend**: Java 21, Spring Boot 3.5.10
-   **Data**: Spring Data JPA, MySQL 9.6
-   **Build Tool**: Gradle
-   **External API**: Public Data Portal (ALIO) Recruitment API

---

## ✨ Key Features

### 1\. 데이터 수집 파이프라인 (Data Pipeline)

-   **Open API 연동**: RestTemplate 및 UriComponentsBuilder를 사용하여 공공기관 채용 정보를 동적으로 수집한다.
-   **상세 데이터 적재**: 목록 API와 상세 API를 연쇄적으로 호출하여 공고 내용, 지원 자격, 전형 절차 등 깊이 있는 정보를 확보한다.

### 2\. 견고한 데이터 모델링

-   **중복 수집 방지**: SourcePlatform과 OriginalSN을 조합한 **Unique Constraint**를 설정하여 동일 공고가 중복 적재되는 것을 방지한다.
-   **데이터 정규화**: 채용 공고를 중심으로 기술 스택(TechStack), 첨부파일(JobFile) 정보를 관계형 데이터베이스로 관리한다.

### 3\. RESTful API 제공

-   **Paging & Sorting**: Spring Data JPA의 Pageable을 활용해 대량의 데이터를 효율적으로 서빙한다.
-   **CORS 대응**: @CrossOrigin 설정을 통해 React 등 모던 프론트엔드 프레임워크와의 원활한 연동을 지원한다.

---

## 🔍 Trouble Shooting & Lessons Learned

| **문제 상황** | **원인** | **해결 방안** |
| --- | --- | --- |
| **JSON Deserialization Error** | API 응답이 상황에 따라 List 또는 Object로 동적 변화 | 상세 조회 전용 DTO를 분리 설계하여 타입 안정성 확보 |
| **Type Mismatch (SN)** | 숫자형 고유번호가 문자열로 유입될 때 파싱 에러 발생 | 모든 식별자 도메인을 String으로 통일하여 확장성 고려 |
| **Port Conflict (8080)** | 이전 프로세스의 비정상 종료로 인한 포트 잠김 | taskkill 명령어를 통한 프로세스 관리 및 포트 우회 설정 적용 |

---

## 🚦 Getting Started

### Prerequisites

-   JDK 21
-   MySQL Server 9.6 이상

### Installation

1.  Repository 클론
2.  Bash
    
    ```
    git clone https://github.com/your-username/devjobcollector.git
    ```
    
3.  src/main/resources/application.yml 설정
    -   MySQL 접속 정보(url, username, password) 수정
    -   공공데이터포털 API 서비스 키 입력
4.  프로젝트 빌드 및 실행
5.  Bash
    
    ```
    ./gradlew bootRun
    ```
    

### API Endpoints

-   **수집 실행**: GET /api/v1/collect/public
-   **공고 목록**: GET /api/v1/jobs?page=0&size=10
-   **공고 상세**: GET /api/v1/jobs/{id}

---

## 📈 Future Roadmap

-   \[ \] 수집 스케줄러(Spring Scheduler) 적용을 통한 자동화
-   \[ \] 키워드 기반 채용 공고 필터링 기능
-   \[ \] React를 활용한 대시보드 UI 구현