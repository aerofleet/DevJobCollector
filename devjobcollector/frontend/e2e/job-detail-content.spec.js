import { expect, test } from '@playwright/test';

const jobDetail = {
  id: 42,
  title: '플랫폼 백엔드 개발자',
  companyName: '데브잡스랩',
  location: '서울',
  experience: '3년 이상',
  startDate: '2026-09-01',
  endDate: '2099-12-31',
  originalUrl: 'https://example.com/jobs/42',
  techStacks: [],
  processInfo: '1. 서류 전형\n2. 기술 인터뷰\n3. 최종 인터뷰',
  applyQual: '<p>주요업무:</p><ul><li>Java와 Spring 기반 서비스를 개발합니다.</li><li>코드 리뷰와 설계 개선에 참여합니다.</li></ul>\n자격요건:\n• 백엔드 개발 경력 3년 이상\n• HTTP와 데이터베이스에 대한 이해\nAbout the team:\n제품 조직과 긴밀하게 협업합니다.\n<img src=x onerror="window.__jobContentInjected=true">\n※ 자세한 안내: https://example.com/guide',
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/42', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(jobDetail),
  }));
});

test('상세 내용을 제목, 목록, 안내 링크로 구조화해 표시한다', async ({ page }) => {
  await page.goto('/job/42');

  const detail = page.locator('.structured-job-content').nth(1);
  await expect(detail.getByRole('heading', { name: '주요업무' })).toBeVisible();
  await expect(detail.getByRole('heading', { name: '자격요건' })).toBeVisible();
  await expect(detail.getByRole('heading', { name: 'About the team' })).toBeVisible();
  await expect(detail.locator('li')).toHaveCount(4);
  await expect(detail.getByRole('link', { name: 'https://example.com/guide' })).toHaveAttribute('rel', 'noopener noreferrer');
  expect(await page.evaluate(() => window.__jobContentInjected)).toBeUndefined();
});

test('모바일에서도 본문과 하단 액션을 화면 너비 안에 표시한다', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-360', '모바일 프로젝트 전용 평가');
  await page.goto('/job/42');

  const contentBox = await page.locator('.structured-job-content').nth(1).boundingBox();
  const actionBox = await page.locator('.action-buttons').boundingBox();
  expect(contentBox.x).toBeGreaterThanOrEqual(0);
  expect(contentBox.x + contentBox.width).toBeLessThanOrEqual(360);
  expect(actionBox.x + actionBox.width).toBeLessThanOrEqual(360);
});
