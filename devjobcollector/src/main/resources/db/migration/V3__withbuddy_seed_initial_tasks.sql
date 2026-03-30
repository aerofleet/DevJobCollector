-- V3__withbuddy_seed_initial_tasks.sql
-- withbuddy 초기 사용자 및 기본 로드맵 태스크 데이터

-- Users
INSERT INTO users (email, name, role, position, tech_stack)
SELECT 'dev-a@withbuddy.local', '개발자 A', 'BACKEND', 'Backend Developer', 'Spring Boot, MySQL'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'dev-a@withbuddy.local');

INSERT INTO users (email, name, role, position, tech_stack)
SELECT 'dev-b@withbuddy.local', '개발자 B', 'BACKEND', 'Backend Developer', 'Spring Boot, FastAPI, AI'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'dev-b@withbuddy.local');

-- Tasks
INSERT INTO roadmap_tasks (
    category_id,
    phase_id,
    period_id,
    assignee_user_id,
    title,
    description,
    status,
    priority
)
SELECT
    c.id,
    ph.id,
    pe.id,
    u.id,
    s.title,
    s.description,
    'TODO',
    s.priority
FROM (
    SELECT 'cat-login' AS category_code, 'phase-1' AS phase_code, 'Week 1-2' AS period_label, 'dev-a@withbuddy.local' AS assignee_email, '인증/인가 시스템 (JWT, 세션)' AS title, '로그인 카테고리 핵심 인증 기능' AS description, 'HIGH' AS priority
    UNION ALL SELECT 'cat-login', 'phase-1', 'Week 1-2', 'dev-a@withbuddy.local', '사용자 관리 API (회원가입/로그인/프로필)', '회원 인증과 사용자 기본 정보 API', 'HIGH'
    UNION ALL SELECT 'cat-dashboard', 'phase-1', 'Week 1-2', 'dev-a@withbuddy.local', '기본 데이터 API (프로필 정보)', '홈 대시보드 기본 데이터 제공', 'MEDIUM'

    UNION ALL SELECT 'cat-qa', 'phase-1', 'Week 1-2', 'dev-b@withbuddy.local', 'FastAPI 연동 환경 구축', 'Q&A 준비를 위한 연동 인프라 구성', 'HIGH'
    UNION ALL SELECT 'cat-qa', 'phase-1', 'Week 1-2', 'dev-b@withbuddy.local', '문서 저장소 구조 설계', '문서 검색을 위한 저장 구조 정의', 'HIGH'
    UNION ALL SELECT 'cat-docs', 'phase-1', 'Week 1-2', 'dev-b@withbuddy.local', '사내 문서 업로드/관리 API', '문서함 초기 CRUD 제공', 'MEDIUM'

    UNION ALL SELECT 'cat-record', 'phase-1', 'Week 3-4', 'dev-a@withbuddy.local', '데일리 기록 CRUD API', '기록 페이지 핵심 CRUD', 'MEDIUM'
    UNION ALL SELECT 'cat-onboarding', 'phase-1', 'Week 3-4', 'dev-a@withbuddy.local', '진행률 트래킹 시스템', '온보딩 진행 상태 집계', 'HIGH'
    UNION ALL SELECT 'cat-onboarding', 'phase-1', 'Week 3-4', 'dev-a@withbuddy.local', '주차별 로드맵 데이터 관리 API', '주차 데이터 조회/수정', 'HIGH'
    UNION ALL SELECT 'cat-onboarding', 'phase-1', 'Week 3-4', 'dev-a@withbuddy.local', '부서별 체크리스트 CRUD', '체크리스트 기본 기능', 'MEDIUM'

    UNION ALL SELECT 'cat-qa', 'phase-1', 'Week 3-4', 'dev-b@withbuddy.local', '문서 검색 API (벡터 검색)', '문서 기반 질의 준비', 'HIGH'
    UNION ALL SELECT 'cat-qa', 'phase-1', 'Week 3-4', 'dev-b@withbuddy.local', '기본 질의응답 API', 'Q&A MVP 핵심 기능', 'HIGH'
    UNION ALL SELECT 'cat-docs', 'phase-1', 'Week 3-4', 'dev-b@withbuddy.local', '문서 분류 및 태깅', '문서함 분류 체계', 'MEDIUM'
    UNION ALL SELECT 'cat-docs', 'phase-1', 'Week 3-4', 'dev-b@withbuddy.local', '권한 기반 문서 접근', '권한별 문서 접근 제어', 'MEDIUM'

    UNION ALL SELECT 'cat-dashboard', 'phase-1', 'Week 5-6', 'dev-a@withbuddy.local', '통계 API 및 위젯 데이터', '대시보드 지표 제공', 'MEDIUM'
    UNION ALL SELECT 'cat-record', 'phase-1', 'Week 5-6', 'dev-a@withbuddy.local', '주간 회고/피드백 API', '기록 페이지 고도화', 'MEDIUM'
    UNION ALL SELECT 'cat-onboarding', 'phase-1', 'Week 5-6', 'dev-a@withbuddy.local', '진행률 시각화 데이터 API', '진행률 차트용 데이터', 'MEDIUM'

    UNION ALL SELECT 'cat-qa', 'phase-1', 'Week 5-6', 'dev-b@withbuddy.local', '답변 정확도 개선', 'Q&A 품질 개선', 'MEDIUM'
    UNION ALL SELECT 'cat-qa', 'phase-1', 'Week 5-6', 'dev-b@withbuddy.local', '다중 문서 참조 기능', '복수 문서 기반 답변', 'MEDIUM'
    UNION ALL SELECT 'cat-docs', 'phase-1', 'Week 5-6', 'dev-b@withbuddy.local', '문서 버전 관리', '문서 변경 이력 추적', 'MEDIUM'

    UNION ALL SELECT 'cat-dashboard', 'phase-2', 'Week 10-11', 'dev-a@withbuddy.local', '개인화된 위젯 시스템', '사용자 맞춤 대시보드 구성', 'MEDIUM'
    UNION ALL SELECT 'cat-onboarding', 'phase-2', 'Week 10-11', 'dev-a@withbuddy.local', '단계별 가이드 API', '온보딩 단계 가이드 제공', 'MEDIUM'
    UNION ALL SELECT 'cat-qa', 'phase-2', 'Week 10-11', 'dev-b@withbuddy.local', '유사 질문 추천 시스템', '질문 추천 로직 구현', 'MEDIUM'
    UNION ALL SELECT 'cat-qa', 'phase-2', 'Week 10-11', 'dev-b@withbuddy.local', 'AI 답변 피드백 수집', '피드백 데이터 수집 API', 'MEDIUM'
    UNION ALL SELECT 'cat-docs', 'phase-2', 'Week 10-11', 'dev-b@withbuddy.local', '문서 버전 관리 고도화', '문서함 개선 작업', 'LOW'

    UNION ALL SELECT 'cat-dashboard', 'phase-2', 'Week 12-13', 'dev-a@withbuddy.local', '부서별/직급별 통계 API', '고급 통계 데이터 제공', 'MEDIUM'
    UNION ALL SELECT 'cat-onboarding', 'phase-2', 'Week 12-13', 'dev-a@withbuddy.local', '평가 및 피드백 수집', '온보딩 평가 데이터 수집', 'MEDIUM'
    UNION ALL SELECT 'cat-docs', 'phase-2', 'Week 12-13', 'dev-b@withbuddy.local', '자동 태깅 및 분류', '문서 자동 분류 고도화', 'MEDIUM'

    UNION ALL SELECT 'cat-onboarding', 'phase-3', 'Week 16-17', 'dev-a@withbuddy.local', '온보딩 프로세스 최적화', '실사용자 피드백 반영 최적화', 'HIGH'
    UNION ALL SELECT 'cat-qa', 'phase-3', 'Week 16-17', 'dev-b@withbuddy.local', 'AI 품질 모니터링', '운영 단계 품질 점검', 'HIGH'
    UNION ALL SELECT 'cat-qa', 'phase-3', 'Week 16-17', 'dev-b@withbuddy.local', '검색 정확도 개선', '검색 성능 및 품질 보정', 'HIGH'
) s
JOIN categories c
  ON c.code = s.category_code
JOIN phases ph
  ON ph.code = s.phase_code
JOIN periods pe
  ON pe.phase_id = ph.id
 AND pe.label = s.period_label
LEFT JOIN users u
  ON u.email = s.assignee_email
LEFT JOIN roadmap_tasks t
  ON t.title = s.title
 AND t.period_id = pe.id
 AND ((t.assignee_user_id IS NULL AND u.id IS NULL) OR t.assignee_user_id = u.id)
WHERE t.id IS NULL;
