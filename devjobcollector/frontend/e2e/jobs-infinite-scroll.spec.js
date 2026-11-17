import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('djc-analytics-consent', 'denied'));
});

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

test('공고 카드에 기술 대신 직무를 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => respond(route, [{
    ...makeJobs(985, 1)[0], techStacks: [{ id: 1, stackName: 'Docker' }],
    mentionedTechStacks: ['Docker', 'Kubernetes', 'AWS', 'CI/CD'],
  }], 0, 1));
  await page.goto('/jobs');
  const card = page.locator('.all-jobs-grid .job-card').first();
  await expect(card.locator('.job-role-badge')).toHaveText(['백엔드']);
  await expect(card.locator('.tech-badge')).toHaveCount(0);
});

test('기술 프로그램 매니저 공고는 직무를 표시하고 도구를 직무로 표시하지 않는다', async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => respond(route, [{
    ...makeJobs(13238, 1)[0], companyName: 'Pinterest', title: 'Technical Program Manager II',
    jobCategory: 'Oversight Engineering', mentionedTechStacks: ['AWS', 'Grafana', 'SQL'],
  }], 0, 1));
  await page.goto('/jobs');
  const card = page.locator('.all-jobs-grid .job-card').first();
  await expect(card.locator('.job-role-badge')).toHaveText(['기술 프로그램 매니저']);
  await expect(card).not.toContainText(/AWS|Grafana|SQL/);
});

test('개발 외 직무와 분리하기 어려운 공고명은 실제 제목을 기준으로 표시한다', async ({ page }) => {
  const longTitle = '특정 직무 분류가 없는 공개 채용 안내 '.repeat(8);
  await page.route('**/api/v1/jobs/search**', route => respond(route, [
    { ...makeJobs(12805, 1)[0], title: '[한국장애인고용공단 대구직업능력개발원] 특정업무직(미화원) 채용공고' },
    { ...makeJobs(2, 1)[0], title: longTitle },
  ], 0, 1));
  await page.goto('/jobs');
  const cards = page.locator('.all-jobs-grid .job-card');
  await expect(cards.nth(0).locator('.job-role-badge')).toHaveText('미화원');
  const fallback = cards.nth(1).locator('.job-role-badge');
  await expect(fallback).toHaveText(longTitle.trim());
  await expect(fallback).toHaveAttribute('title', longTitle.trim());
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('영문 비개발 공고명에서 명확한 직무를 간략하게 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => respond(route, [
    { ...makeJobs(3, 1)[0], title: 'Seller Compliance Manager' },
    { ...makeJobs(4, 1)[0], title: 'Real Estate Transactions Manager' },
    { ...makeJobs(5, 1)[0], title: 'Cinematographer/Video Editor' },
  ], 0, 1));
  await page.goto('/jobs');
  await expect(page.locator('.all-jobs-grid .job-role-badge')).toHaveText(['컴플라이언스 매니저', '부동산 거래 매니저', '영상 촬영·편집']);
});

test('인사·고객 엔지니어링·현지화·마케팅·사업개발 직무를 축약한다', async ({ page }) => {
  const titles = [
    'People Team Intern, EMEA Employment Compliance & Manager Enablement (Spring 2027)',
    'Senior Manager, Customer Engineering',
    '[네이버웹툰] 일본어 Localization PM (한국어-일본어) (계약직)',
    'Sr. Regional Marketing Manager - UKI',
    'Business Development Representative - French Speaking',
  ];
  await page.route('**/api/v1/jobs/search**', route => respond(route,
    titles.map((title, index) => ({ ...makeJobs(13276 + index, 1)[0], title })), 0, 1));
  await page.goto('/jobs');
  await expect(page.locator('.all-jobs-grid .job-role-badge')).toHaveText([
    '인사팀 인턴', '고객 엔지니어링 매니저', '일본어 현지화 PM', '지역 마케팅 매니저', '사업개발 담당자',
  ]);
});

test('개발자 채용 메뉴는 목록 중간에서 눌러도 상단으로 이동한다', async ({ page }, testInfo) => {
  await page.route('**/api/v1/jobs/search**', route => respond(route, makeJobs(1, 12), 0, 1));
  await page.goto('/jobs?keyword=backend');
  await expect(page.locator('.all-jobs-grid .job-card')).toHaveCount(12);
  await page.evaluate(() => window.scrollTo(0, 900));
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(100);
  if (testInfo.project.name === 'mobile-360' || testInfo.project.name === 'tablet-768') {
    await page.getByRole('navigation', { name: '모바일 빠른 메뉴' }).getByRole('link', { name: '채용', exact: true }).click();
  } else {
    await page.evaluate(() => window.scrollBy(0, -150));
    await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: '개발자 채용' }).click();
  }
  await expect(page).toHaveURL(/\/jobs$/);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
});

test('상세에서 돌아가면 필터와 추가로 불러온 목록 및 스크롤 위치를 복원한다', async ({ page }) => {
  let blockRefresh = false;
  let releaseRefresh;
  const refreshGate = new Promise(resolve => { releaseRefresh = resolve; });
  await page.route('**/api/v1/jobs/search**', async route => {
    if (blockRefresh) await refreshGate;
    const requestedPage = Number(new URL(route.request().url()).searchParams.get('page') ?? 0);
    return respond(route, makeJobs(requestedPage * 12 + 1, 12), requestedPage, 3);
  });
  await page.route('**/api/v1/jobs/20?*', route => route.fulfill({ json: {
    ...makeJobs(20, 1)[0], sourcePlatform: 'GREENHOUSE', originalUrl: 'https://example.com/job/20',
  } }));
  await page.goto('/jobs?keyword=backend');
  await expect(page.locator('.all-jobs-grid .job-card')).toHaveCount(12);
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await expect.poll(() => page.locator('.all-jobs-grid .job-card').count()).toBeGreaterThanOrEqual(24);
  const target = page.locator('.all-jobs-grid .job-card').filter({ hasText: '백엔드 개발자 20' }).first();
  await target.scrollIntoViewIfNeeded();
  const before = await page.evaluate(() => window.scrollY);
  await target.click();
  await expect(page).toHaveURL(/\/job\/20(?:\/[^/?#]+)?$/);
  blockRefresh = true;
  await page.getByRole('button', { name: '← 돌아가기' }).click();
  await expect(page).toHaveURL(/\/jobs\?keyword=backend$/);
  await expect.poll(() => page.locator('.all-jobs-grid .job-card').count()).toBeGreaterThanOrEqual(24);
  await expect.poll(async () => Math.abs((await page.evaluate(() => window.scrollY)) - before)).toBeLessThan(5);
  await expect(page.getByRole('status', { name: '공고 불러오는 중' })).toHaveCount(0);
  releaseRefresh();
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
  await expect(page.locator('.job-match-snippet')).toHaveCount(0);
});

test('5분 이상 탭을 떠난 뒤 복귀하면 숨김 공고를 다시 검색해 제거한다', async ({ page }) => {
  const requestKeys = [];
  let hidden = false;
  await page.route('**/api/v1/jobs/search**', async (route) => {
    const url = new URL(route.request().url());
    requestKeys.push(url.searchParams.get('_fresh'));
    const content = hidden ? [] : [{ ...makeJobs(9, 1)[0], companyName: '한국탄소산업진흥원' }];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      content, totalPages: content.length ? 1 : 0, totalElements: content.length,
      number: 0, size: 12,
    }) });
  });

  await page.goto('/jobs?keyword=한국탄소산업진흥원');
  await expect(page.locator('.job-card')).toHaveCount(1);
  hidden = true;
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    document.dispatchEvent(new Event('visibilitychange'));
    const now = Date.now();
    Date.now = () => now + 301_000;
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await expect(page.locator('.job-card')).toHaveCount(0);
  await expect(page.getByText('조건에 맞는 공고 0개')).toBeVisible();
  expect(requestKeys.length).toBeGreaterThanOrEqual(2);
  expect(requestKeys.every(Boolean)).toBe(true);
  expect(new Set(requestKeys).size).toBe(requestKeys.length);
});
