import { expect, test } from '@playwright/test';

const jobDetail = {
  id: 42,
  sourcePlatform: 'GREENHOUSE',
  title: '플랫폼 백엔드 개발자',
  companyName: '데브잡스랩',
  location: '서울',
  experience: '3년 이상',
  startDate: '2026-09-01',
  endDate: '2099-12-31',
  originalUrl: 'https://example.com/jobs/42',
  techStacks: [],
  processInfo: '1. 서류 전형\n2. 기술 인터뷰\n3. 최종 인터뷰',
  applyQual: '◆ 업무내용 - Java와 Spring 기반 서비스를 개발합니다. ◆ 지원자격 - 백엔드 개발 경력 3년 이상 - HTTP와 데이터베이스에 대한 이해 ◆ 우대사항 - 클라우드 운영 경험 ◆ 접수 방법 - https://example.com/guide',
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/42?*', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(jobDetail),
  }));
});

test('공고 원문 대신 출처와 자격 조건을 표시하고 원문으로 연결한다', async ({ page }) => {
  await page.goto('/job/42');

  await expect(page.getByText('출처 · Greenhouse')).toBeVisible();
  await expect(page.getByRole('heading', { name: '자격 조건' })).toBeVisible();
  await expect(page.getByText('백엔드 개발 경력 3년 이상', { exact: true })).toBeVisible();
  await expect(page.getByText('클라우드 운영 경험', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '담당 업무' }).locator('..')).toContainText('Java와 Spring 기반 서비스를 개발합니다.');
  await expect(page.locator('details').getByText('Java와 Spring 기반 서비스를 개발합니다.')).not.toBeVisible();
  await page.getByText('수집된 공고 내용 전체 보기').click();
  await expect(page.locator('details').getByText('Java와 Spring 기반 서비스를 개발합니다.')).toBeVisible();
  await expect(page.getByRole('link', { name: '공고 원문 보기 ↗' })).toHaveAttribute('href', jobDetail.originalUrl);
  expect(await page.evaluate(() => window.__jobContentInjected)).toBeUndefined();
});

test('모바일에서도 핵심 항목과 하단 액션을 화면 너비 안에 표시한다', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-360', '모바일 프로젝트 전용 평가');
  await page.goto('/job/42');

  const contentBox = await page.getByRole('heading', { name: '자격 조건' }).boundingBox();
  const actionBox = await page.locator('.action-buttons').boundingBox();
  expect(contentBox.x).toBeGreaterThanOrEqual(0);
  expect(contentBox.x + contentBox.width).toBeLessThanOrEqual(360);
  expect(actionBox.x + actionBox.width).toBeLessThanOrEqual(360);
});

test('알리오 공고의 제공된 지원 자격과 전형 방법을 전체 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/43?*', (route) => route.fulfill({ json: {
    ...jobDetail, id: 43, sourcePlatform: 'PUBLIC_ALIO', applyQual: '지원 자격 상세 문구\n추가 자격 문구',
    processInfo: '서류 전형\n면접 전형', publicPreference: '장애인 우대', publicPreferenceConditions: '관련 자격증 우대', publicDisqualification: '결격 사유 안내', files: [{ id: 1, fileName: '채용 공고.pdf', fileUrl: 'https://example.com/notice.pdf' }],
  } }));
  await page.goto('/job/43');
  await expect(page.getByText('출처 · 공공기관 알리오')).toBeVisible();
  await expect(page.getByText(/지원 자격 상세 문구/)).toBeVisible();
  await expect(page.getByText(/추가 자격 문구/)).toBeVisible();
  await expect(page.getByText(/면접 전형/)).toBeVisible();
  await expect(page.getByText('장애인 우대')).toBeVisible();
  await expect(page.getByText('관련 자격증 우대')).toBeVisible();
  await expect(page.getByText('결격 사유 안내')).toBeVisible();
  await expect(page.getByRole('link', { name: '채용 공고.pdf' })).toHaveAttribute('href', 'https://example.com/notice.pdf');
  await expect(page.getByRole('link', { name: '공고 원문 보기 ↗' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /지원하기/ })).toBeEnabled();
});

test('별도 원문에서 수집한 급여의 조건을 함께 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/44?*', route => route.fulfill({ json: {
    ...jobDetail, id: 44, supplementalSalaryText: 'Annual OTE A$306,500 - A$459,700 for the primary location. Includes base salary and sales commissions.',
  } }));
  await page.goto('/job/44');
  await expect(page.getByText(/Annual OTE A\$306,500/)).toBeVisible();
  await expect(page.getByText(/primary location/)).toBeVisible();
  await expect(page.getByText(/Includes base salary and sales commissions/)).toBeVisible();
});

test('토스형 공고의 자격과 우대 경험을 구분해 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/11108?*', route => route.fulfill({ json: {
    ...jobDetail, id: 11108, companyName: '토스', sourcePlatform: 'COMPANY_PAGE', originalUrl: 'https://toss.im/career/job-detail?job_id=8099818003',
    endDate: '9999-12-31', experience: '경력 확인 필요', applyQual: '# 합류하면 함께 할 업무예요\n- 자금 집행\n# 이런 분과 함께하고 싶어요\n- 자금 오퍼레이션 업무를 2년 이상 경험하신 분이면 좋아요\n- SAP 실무 경험을 반드시 갖추신 분\n# 이런 경험이 있다면 더 좋아요\n- 자금 프로세스 개선 경험\n# 토스로의 합류 여정\n- 서류 접수',
  } }));
  await page.goto('/job/11108');
  await expect(page.locator('.info-item').filter({ hasText: '경력' })).toContainText('2년 이상');
  await expect(page.getByRole('heading', { name: '자격 조건' }).locator('..')).toContainText('자금 오퍼레이션 업무를 2년 이상');
  await expect(page.getByRole('heading', { name: '우대 사항' }).locator('..')).toContainText('자금 프로세스 개선 경험');
  await expect(page.getByRole('heading', { name: '우대 사항' }).locator('..')).not.toContainText('서류 접수');
  await expect(page.getByText('SAP', { exact: true })).toBeVisible();
});

test('원문에 기술 목록이 있으면 등록 태그가 없어도 목록을 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/11105?*', route => route.fulfill({ json: {
    ...jobDetail, id: 11105, techStacks: [], applyQual: '# 토스가 사용하는 기술\n- 코어: React, TypeScript, Next.js\n# 토스로의 합류 여정\n- 서류 접수',
  } }));
  await page.goto('/job/11105');
  await expect(page.getByRole('heading', { name: '기술 스택' }).locator('..')).toContainText('코어: React, TypeScript, Next.js');
});

test('카카오 원문에서 확인한 영입종료시만 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/9439?*', route => route.fulfill({ json: {
    ...jobDetail, id: 9439, sourcePlatform: 'KAKAO', endDate: '9999-12-31', sourceDeadlineDisplay: 'RECRUITMENT_END',
    deadlineEvidence: { status: 'UNAVAILABLE' },
  } }));
  await page.goto('/job/9439');
  await expect(page.locator('.info-item').filter({ hasText: '마감일' }).locator('.value')).toHaveText('영입종료시');
});

test('카카오 원문에서 기간이 숨겨진 경우 마감일을 단정하지 않는다', async ({ page }) => {
  await page.route('**/api/v1/jobs/9440?*', route => route.fulfill({ json: {
    ...jobDetail, id: 9440, sourcePlatform: 'KAKAO', endDate: '2030-12-31', sourceDeadlineDisplay: 'HIDDEN',
    deadlineEvidence: { status: 'PRESENT', localDate: '2030-12-31' },
  } }));
  await page.goto('/job/9440');
  await expect(page.locator('.info-item').filter({ hasText: '마감일' }).locator('.value')).toHaveText('마감일 확인 필요');
  await expect(page.locator('.days-badge-d')).toHaveCount(0);
});
