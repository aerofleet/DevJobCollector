import { expect, test } from '@playwright/test';

const makeJobs = (start, count) => Array.from({ length: count }, (_, index) => ({
  id: start + index,
  companyName: `테스트 기업 ${start + index}`,
  title: `백엔드 개발자 ${start + index}`,
  location: '서울',
  experience: '경력무관',
  hireType: '정규직',
  endDate: '2099-12-31',
  techStacks: [],
}));

const respond = (route, content, page, totalPages = 3) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({
    content,
    totalPages,
    totalElements: 30,
    number: page,
    size: 12,
  }),
});

test('랜딩 하단 링크로 이동해 초기 로딩 중 scroll이 발생해도 다음 페이지를 불러온다', async ({ page }) => {
  let releaseInitialJobs;
  const initialJobsGate = new Promise((resolve) => {
    releaseInitialJobs = resolve;
  });
  const requestedPages = [];

  await page.route('**/api/v1/jobs/search**', async (route) => {
    const url = new URL(route.request().url());
    const keyword = url.searchParams.get('keyword') ?? '';
    const requestedPage = Number(url.searchParams.get('page') ?? 0);

    if (keyword) return respond(route, makeJobs(100, 8), 0, 1);

    requestedPages.push(requestedPage);
    if (requestedPage === 0) {
      await initialJobsGate;
      return respond(route, makeJobs(1, 12), 0);
    }
    return respond(route, makeJobs((requestedPage * 12) + 1, 12), requestedPage);
  });

  await page.goto('/');
  const allJobsLink = page.getByRole('link', { name: '전체 공고 보기' });
  await allJobsLink.scrollIntoViewIfNeeded();
  await allJobsLink.click();
  await expect(page).toHaveURL(/\/jobs$/);

  await page.evaluate(() => window.dispatchEvent(new Event('scroll')));
  releaseInitialJobs();

  await expect(page.locator('.all-jobs-grid .job-card').first()).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));

  await expect.poll(() => requestedPages.includes(1)).toBe(true);
  await expect.poll(() => page.locator('.all-jobs-grid .job-card').count()).toBeGreaterThanOrEqual(24);
});
