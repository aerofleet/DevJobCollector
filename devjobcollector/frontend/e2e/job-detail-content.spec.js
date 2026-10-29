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
  await expect(page.getByText('백엔드 개발 경력 3년 이상')).toBeVisible();
  await expect(page.getByText('클라우드 운영 경험')).toBeVisible();
  await expect(page.getByText('Java와 Spring 기반 서비스를 개발합니다.')).toHaveCount(0);
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
