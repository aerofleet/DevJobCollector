import { test, expect } from '@playwright/test';

test('홈의 모든 테마는 해당 직무 또는 신입 필터로 연결된다', async ({ page }) => {
  await page.goto('/');
  const cards = page.locator('.theme-card');
  const expected = [
    [['jobCategory', 'devops,sre,cloud,infrastructure,network,platform']],
    [['experience', '신입'], ['jobCategory', 'backend,frontend,fullstack,mobile,data-engineer,data-scientist,ml-engineer,ai-engineer,mlops,computer-vision,nlp,devops,sre,cloud,platform,embedded,robotics,iot,blockchain,desktop']],
    [['jobCategory', 'backend']],
    [['jobCategory', 'frontend,web-publishing']],
    [['jobCategory', 'data-ai,data-engineer,data-analyst,data-scientist,ml-engineer,ai-engineer,mlops,computer-vision,nlp']],
    [['jobCategory', 'mobile,android,ios,cross-platform']],
  ];
  await expect(cards).toHaveCount(expected.length);
  for (let i = 0; i < expected.length; i++) {
    const url = new URL(await cards.nth(i).getAttribute('href'), page.url());
    expect([...url.searchParams.entries()]).toEqual(expected[i]);
  }
});

test('기존 신입 검색 링크는 다른 필터를 유지하며 신입 API 필터로 전환된다', async ({ page }) => {
  const requests = [];
  await page.route('**/api/v1/jobs/search**', route => {
    requests.push(new URL(route.request().url()));
    return route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } });
  });
  await page.route('**/api/v1/jobs/companies**', route => route.fulfill({ json: [] }));
  await page.goto('/jobs?keyword=신입&jobCategory=backend');
  await expect.poll(() => new URL(page.url()).searchParams.get('experience')).toBe('신입');
  await expect.poll(() => requests.length).toBeGreaterThan(0);
  for (const url of requests) {
    expect(url.searchParams.get('experience')).toBe('신입');
    expect(url.searchParams.get('keyword') || '').toBe('');
    expect(url.searchParams.get('jobCategory')).toBe('backend');
  }
  expect(new URL(page.url()).searchParams.has('keyword')).toBe(false);
});
