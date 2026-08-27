# Career Hub Resume Platform API 계약

작성일: 2026-08-27  
상태: CH-P4-01 확정

## 1. 목표와 범위

현재 `ResumeService`의 프로세스 메모리 저장소를 V4 `resumes` 테이블 기반 영속 저장으로 교체한다. API는 JWT subject로 확인한 ACTIVE 회원만 접근하며, 요청에서 `userId`를 받지 않는다.

이번 계약의 범위는 회원별 이력서 목록·생성·조회·전체 수정·상태 변경·삭제이다. 이력서 공개 URL, 파일/PDF 변환, 버전 이력, 협업 편집은 범위 밖이다.

## 2. 현재 구현 감사

| 항목 | 현재 상태 | 전환 결정 |
|---|---|---|
| 저장소 | `ConcurrentHashMap<Long, ResumeDTO>` | `CareerResumeRepository`와 MySQL V4 `resumes` 사용 |
| 식별자 | 저장 ID와 `GET /resume/{userId}` 의미가 충돌 | 모든 리소스 식별자는 `resumeId`; 회원은 JWT subject로 결정 |
| 소유권 | 요청 경로의 사용자 ID를 신뢰 | 모든 단건 쿼리는 `findByIdAndUser_Id` 사용 |
| 재시작 | 데이터 전부 유실 | 트랜잭션 커밋 후 재조회 및 애플리케이션 재시작 평가 |
| 프런트 계약 | 단일 `POST /resume`, 고정 빈 관리 카드 | `/members/me/resumes` CRUD와 실제 목록 연결 |
| 데이터 구조 | `basicInfo`, `techStack`, `projects`, `experience` | 동일 구조를 `content_json`에 보존해 UI 회귀 최소화 |

## 3. 공통 계약

- Base path: `/api/v1/members/me/resumes`
- 인증: Bearer JWT 필수
- 회원 판정: `CurrentMemberService.requireCurrentMember(subject)`
- 정렬: `updatedAt DESC, id DESC`
- 타 회원 소유 ID: 리소스 존재 여부를 숨기기 위해 HTTP 404
- 잘못된 본문·상태: HTTP 400
- 비인증·만료·비활성·삭제 프로필: HTTP 401
- 삭제 성공: HTTP 204
- 날짜/시각: ISO-8601

## 4. 엔드포인트

| Method | Path | 성공 | 설명 |
|---|---|---:|---|
| `GET` | `/api/v1/members/me/resumes` | 200 | 본인 이력서 목록 |
| `POST` | `/api/v1/members/me/resumes` | 201 | DRAFT 이력서 생성 |
| `GET` | `/api/v1/members/me/resumes/{resumeId}` | 200 | 본인 이력서 상세 |
| `PUT` | `/api/v1/members/me/resumes/{resumeId}` | 200 | 제목과 본문 전체 교체 |
| `PATCH` | `/api/v1/members/me/resumes/{resumeId}/status` | 200 | 상태 변경 |
| `DELETE` | `/api/v1/members/me/resumes/{resumeId}` | 204 | 본인 이력서 삭제 |

### 4.1 생성·전체 수정 요청

```json
{
  "title": "개발자 이력서",
  "content": {
    "basicInfo": {
      "name": "홍길동",
      "email": "member@example.com",
      "phone": "010-0000-0000",
      "birthDate": "2000-01-01",
      "address": "서울"
    },
    "techStack": [],
    "projects": [],
    "experience": []
  }
}
```

- `title`: trim 후 1~150자
- `content`: null 불가 JSON object
- 컬렉션 필드가 누락되면 빈 배열, `basicInfo`가 누락되면 빈 object로 정규화한다.
- `POST`는 클라이언트가 보낸 `id`, `status`, `userId`, 생성·수정 시각을 무시하거나 바인딩하지 않는다.
- `PUT`은 전체 교체이며 소유자와 상태는 변경하지 않는다.

### 4.2 상태 변경 요청

```json
{
  "status": "READY"
}
```

허용 값은 V4 enum과 동일한 `DRAFT`, `READY`, `ARCHIVED`이다.

### 4.3 목록 응답

```json
[
  {
    "id": 17,
    "title": "개발자 이력서",
    "status": "DRAFT",
    "createdAt": "2026-08-27T10:00:00",
    "updatedAt": "2026-08-27T11:30:00"
  }
]
```

목록 응답에는 `content`를 포함하지 않는다.

### 4.4 상세·생성·수정 응답

```json
{
  "id": 17,
  "title": "개발자 이력서",
  "status": "DRAFT",
  "content": {
    "basicInfo": {},
    "techStack": [],
    "projects": [],
    "experience": []
  },
  "createdAt": "2026-08-27T10:00:00",
  "updatedAt": "2026-08-27T11:30:00"
}
```

`POST` 응답에는 생성 리소스의 `Location: /api/v1/members/me/resumes/{id}`를 포함한다.

## 5. 소유권과 트랜잭션

- 목록은 반드시 `user_id = currentMember.id` 조건을 포함한다.
- 조회·수정·상태 변경·삭제는 `findByIdAndUser_Id(resumeId, currentMember.id)`로 조회한다.
- 타 회원 이력서 ID와 존재하지 않는 ID는 모두 같은 404 응답을 사용한다.
- 생성·수정·상태 변경·삭제는 각각 단일 트랜잭션이다.
- 동일 이력서 동시 전체 수정은 마지막 커밋이 승리한다. 낙관적 잠금은 CH-P4 평가에서 실제 갱신 유실이 발견될 때 별도 도입한다.

## 6. 전환 및 제거 계획

1. `ResumeService`의 메모리 필드(`ConcurrentHashMap`, `AtomicLong`)와 전체 조회·개수 메서드를 제거한다.
2. `ResumeController`를 `/api/v1/members/me/resumes` 계약으로 교체한다.
3. 요청·목록·상세·상태 DTO를 `dto.career` 아래에 분리하고 Entity를 API에 직접 노출하지 않는다.
4. `ResumeDTO`의 기존 content 구조는 새 요청/응답 content 타입으로 이동하거나 호환 타입으로 재사용한다.
5. 프런트 `resumeApi.js`를 새 base path로 전환한다.
6. `/resumes`는 실제 목록을 표시하고 `/resume`은 신규, `/resume?resumeId={id}`는 수정 모드로 사용한다.
7. 백엔드와 프런트 전환을 같은 기능 커밋 경계에서 검증하며, 구 `/api/v1/resume/**`는 신규 경로 검증 후 제거한다. 이 API는 현재 운영 데이터가 없는 메모리 구현이므로 별도 데이터 이관은 하지 않는다.

## 7. KPI / OKR / 평가셋

### 목표 KPI

- 본인 이력서 CRUD 성공률 100%
- 타 회원 이력서 접근 차단률 100%
- 비인증 접근 차단률 100%
- 재시작 후 저장 데이터 유지율 100%
- 평가셋 API 5xx 비율 0%
- MySQL 26.7 migration·repository 평가 성공률 100%

### OKR 연결

- Career Hub Product DoD의 이력서 실제 데이터 기능을 완료해 로그인 회원의 핵심 기능 도달률 100% 목표에 기여한다.

### 평가셋

- 서비스: 생성·목록·상세·수정·상태 변경·삭제 6건
- 소유권: 본인 성공, 타 회원 조회·수정·상태 변경·삭제 4건, 비인증 1건
- 검증: 빈 제목, 151자 제목, null content, 잘못된 status 4건
- DB: MySQL 26.7 clean V1→V4와 V3→V4, 저장 후 application context 재생성 1건
- 프런트: 목록 Loading·Empty·Error·Success·Unauthorized 5상태, 신규·수정 저장 2경로
- 동시성: 동일 이력서 수정 20회, concurrency 10에서 5xx 0건과 JSON 유효성 100%

### 합격 기준

- 위 평가셋 failures/errors/skips 0건
- 타 회원 ID에 대한 데이터·소유자 정보 노출 0건
- 애플리케이션 재시작 전후 이력서 ID·제목·본문·상태 일치율 100%
- 프런트 ESLint 오류 0건 및 production build 성공

## 8. 롤백

- V4 DDL은 이미 운영 적용된 additive migration이므로 롤백 시 테이블을 삭제하지 않는다.
- 애플리케이션 롤백은 이전 이미지/JAR로 되돌리되 V4 `resumes` 데이터는 보존한다.
- 구 메모리 API는 영속 데이터의 source of truth가 아니므로 롤백 후 신규 저장을 허용하지 않는 것이 원칙이다.

