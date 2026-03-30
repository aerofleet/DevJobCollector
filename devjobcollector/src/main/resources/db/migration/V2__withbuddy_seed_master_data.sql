-- V2__withbuddy_seed_master_data.sql
-- withbuddy 초기 마스터 데이터

-- Categories
INSERT INTO categories (code, name, description, sort_order)
SELECT 'cat-login', '로그인', '인증/인가 시스템', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'cat-login');

INSERT INTO categories (code, name, description, sort_order)
SELECT 'cat-dashboard', '홈 대시보드', '프로필, 진척도', 2
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'cat-dashboard');

INSERT INTO categories (code, name, description, sort_order)
SELECT 'cat-qa', '사내 문서 Q&A', 'AI 기반 질의응답', 3
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'cat-qa');

INSERT INTO categories (code, name, description, sort_order)
SELECT 'cat-record', '기록 페이지', '데일리/주간 회고', 4
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'cat-record');

INSERT INTO categories (code, name, description, sort_order)
SELECT 'cat-docs', '인사/행정 문서함', '공식 문서 관리', 5
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'cat-docs');

INSERT INTO categories (code, name, description, sort_order)
SELECT 'cat-onboarding', '온보딩 진행률', '로드맵, 체크리스트', 6
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'cat-onboarding');

-- Phases
INSERT INTO phases (code, name, description, start_date, end_date, sort_order)
SELECT 'phase-1', 'MVP 개발', '핵심 6개 카테고리 개발', '2026-03-17', '2026-05-14', 1
WHERE NOT EXISTS (SELECT 1 FROM phases WHERE code = 'phase-1');

INSERT INTO phases (code, name, description, start_date, end_date, sort_order)
SELECT 'phase-2', '서비스 고도화', '핵심 카테고리 개선 및 확장', '2026-05-18', '2026-06-25', 2
WHERE NOT EXISTS (SELECT 1 FROM phases WHERE code = 'phase-2');

INSERT INTO phases (code, name, description, start_date, end_date, sort_order)
SELECT 'phase-3', '런칭 준비', '실사용자 대응 및 최종 안정화', '2026-06-29', '2026-07-16', 3
WHERE NOT EXISTS (SELECT 1 FROM phases WHERE code = 'phase-3');

-- Periods
INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 1-2', '2026-03-17', '2026-03-30', 1
FROM phases p
WHERE p.code = 'phase-1'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 1-2'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 3-4', '2026-03-31', '2026-04-13', 2
FROM phases p
WHERE p.code = 'phase-1'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 3-4'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 5-6', '2026-04-14', '2026-04-27', 3
FROM phases p
WHERE p.code = 'phase-1'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 5-6'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 7-9', '2026-04-28', '2026-05-14', 4
FROM phases p
WHERE p.code = 'phase-1'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 7-9'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 10-11', '2026-05-18', '2026-05-31', 1
FROM phases p
WHERE p.code = 'phase-2'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 10-11'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 12-13', '2026-06-01', '2026-06-14', 2
FROM phases p
WHERE p.code = 'phase-2'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 12-13'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 14', '2026-06-15', '2026-06-21', 3
FROM phases p
WHERE p.code = 'phase-2'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 14'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 15', '2026-06-22', '2026-06-25', 4
FROM phases p
WHERE p.code = 'phase-2'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 15'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 16-17', '2026-06-29', '2026-07-09', 1
FROM phases p
WHERE p.code = 'phase-3'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 16-17'
  );

INSERT INTO periods (phase_id, label, start_date, end_date, sort_order)
SELECT p.id, 'Week 18', '2026-07-10', '2026-07-16', 2
FROM phases p
WHERE p.code = 'phase-3'
  AND NOT EXISTS (
      SELECT 1
      FROM periods x
      WHERE x.phase_id = p.id AND x.label = 'Week 18'
  );
