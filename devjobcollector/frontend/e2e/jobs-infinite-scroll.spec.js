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

test('공고 내용 검색 범위를 안내하고 복합 검색어를 API에 전달한다', async ({ page }) => {
  const requestedKeywords = [];
  await page.route('**/api/v1/jobs/search**', async (route) => {
    const url = new URL(route.request().url());
    const keyword = url.searchParams.get('keyword') ?? '';
    requestedKeywords.push(keyword);
    const jobs = keyword
      ? [{ ...makeJobs(100, 1)[0], matchedSnippet: 'Java와 Kafka 기반 공고 내용 일치 구간' }]
      : [];
    return respond(route, jobs, 0, 1);
  });

  await page.goto('/jobs');
  const searchInput = page.getByRole('textbox', { name: '채용공고 검색' });
  await expect(searchInput).toHaveAttribute('placeholder', '직무, 기술 스택, 공고 내용 검색');
  await searchInput.fill('  Java/Kafka 신입  ');
  await page.locator('.jobs-search-form').getByRole('button', { name: '검색', exact: true }).click();

  await expect(page).toHaveURL(/keyword=Java%2FKafka(?:\+|%20)%EC%8B%A0%EC%9E%85/);
  await expect.poll(() => requestedKeywords.includes('Java/Kafka 신입')).toBe(true);
  await expect(page.locator('.job-match-snippet')).toHaveText('Java와 Kafka 기반 공고 내용 일치 구간');
});
