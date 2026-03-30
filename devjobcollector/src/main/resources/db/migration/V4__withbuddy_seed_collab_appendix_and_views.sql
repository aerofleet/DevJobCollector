-- V4__withbuddy_seed_collab_appendix_and_views.sql
-- 협업/확장 기능 태스크 시드 + 조회용 뷰

-- Collab tasks (assignee 없음)
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
    NULL,
    s.title,
    s.description,
    'TODO',
    s.priority
FROM (
    SELECT 'cat-onboarding' AS category_code, 'phase-1' AS phase_code, 'Week 7-9' AS period_label, '통합 API 테스트 및 버그 수정' AS title, '개발자 A/B 협업 통합 검증' AS description, 'HIGH' AS priority
    UNION ALL SELECT 'cat-dashboard', 'phase-1', 'Week 7-9', '성능 최적화 (쿼리, 캐시, 응답시간)', 'MVP 성능 안정화', 'HIGH'
    UNION ALL SELECT 'cat-login', 'phase-1', 'Week 7-9', '보안 점검 (인증/인가, 입력 검증)', '보안 필수 항목 점검', 'HIGH'
    UNION ALL SELECT 'cat-onboarding', 'phase-1', 'Week 7-9', 'MVP 배포 환경 준비', '운영 배포 전 점검', 'MEDIUM'

    UNION ALL SELECT 'cat-onboarding', 'phase-2', 'Week 15', '전체 시스템 통합 테스트', '고도화 단계 마무리 통합 테스트', 'HIGH'
    UNION ALL SELECT 'cat-dashboard', 'phase-2', 'Week 15', '부하 테스트 및 최적화', '성능 병목 분석 및 개선', 'HIGH'
    UNION ALL SELECT 'cat-login', 'phase-2', 'Week 15', '프로덕션 배포 준비', '운영 이관 체크리스트 수행', 'MEDIUM'

    UNION ALL SELECT 'cat-dashboard', 'phase-3', 'Week 18', '데모 시나리오 API 점검', '엑스포 발표 대응', 'MEDIUM'
    UNION ALL SELECT 'cat-record', 'phase-3', 'Week 18', '엑스포 발표 자료용 데이터 준비', '발표 리포트 데이터 정리', 'LOW'
    UNION ALL SELECT 'cat-onboarding', 'phase-3', 'Week 18', '실시간 모니터링 대시보드', '운영 관측성 개선', 'MEDIUM'
    UNION ALL SELECT 'cat-login', 'phase-3', 'Week 18', '최종 버그 픽스 및 안정화', '런칭 전 품질 마무리', 'HIGH'
) s
JOIN categories c
  ON c.code = s.category_code
JOIN phases ph
  ON ph.code = s.phase_code
JOIN periods pe
  ON pe.phase_id = ph.id
 AND pe.label = s.period_label
LEFT JOIN roadmap_tasks t
  ON t.title = s.title
 AND t.period_id = pe.id
 AND t.assignee_user_id IS NULL
WHERE t.id IS NULL;

-- Appendix tasks (priority: OPTIONAL/LOW)
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
    SELECT 'cat-qa' AS category_code, 'phase-2' AS phase_code, 'Week 12-13' AS period_label, 'dev-b@withbuddy.local' AS assignee_email, '다국어 지원: Q&A 번역 API 연동', '확장 기능 (Nice to Have)', 'LOW' AS priority
    UNION ALL SELECT 'cat-qa', 'phase-2', 'Week 12-13', 'dev-b@withbuddy.local', '음성 질문(STT) 연동', '확장 기능 (Nice to Have)', 'LOW'
    UNION ALL SELECT 'cat-onboarding', 'phase-2', 'Week 12-13', 'dev-a@withbuddy.local', '멘토-멘티 자동 매칭', '확장 기능 (Optional)', 'LOW'
    UNION ALL SELECT 'cat-dashboard', 'phase-2', 'Week 10-11', 'dev-a@withbuddy.local', '실시간 알림(WebSocket) 구축', '확장 기능 (Optional)', 'LOW'
    UNION ALL SELECT 'cat-dashboard', 'phase-2', 'Week 12-13', 'dev-a@withbuddy.local', '관리자 대시보드', '확장 기능 (Optional)', 'LOW'
    UNION ALL SELECT 'cat-record', 'phase-3', 'Week 16-17', 'dev-a@withbuddy.local', '사용자 지원 센터', '확장 기능 (Optional)', 'LOW'
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

-- Views
CREATE OR REPLACE VIEW vw_withbuddy_task_overview AS
SELECT
    t.id AS task_id,
    t.title,
    t.description,
    t.status,
    t.priority,
    c.code AS category_code,
    c.name AS category_name,
    ph.code AS phase_code,
    ph.name AS phase_name,
    pe.label AS period_label,
    u.name AS assignee_name,
    u.email AS assignee_email,
    t.created_at,
    t.updated_at
FROM roadmap_tasks t
JOIN categories c ON c.id = t.category_id
JOIN phases ph ON ph.id = t.phase_id
LEFT JOIN periods pe ON pe.id = t.period_id
LEFT JOIN users u ON u.id = t.assignee_user_id;

CREATE OR REPLACE VIEW vw_withbuddy_progress_summary AS
SELECT
    ph.code AS phase_code,
    ph.name AS phase_name,
    c.code AS category_code,
    c.name AS category_name,
    COUNT(*) AS total_tasks,
    SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) AS done_tasks,
    ROUND(
        IFNULL(SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0) * 100, 0),
        0
    ) AS done_percent
FROM roadmap_tasks t
JOIN categories c ON c.id = t.category_id
JOIN phases ph ON ph.id = t.phase_id
GROUP BY ph.code, ph.name, c.code, c.name;
