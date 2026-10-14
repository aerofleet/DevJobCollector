import { test, expect } from '@playwright/test';

for (const consent of [null, 'granted']) {
test(`JavaScript를 기다리지 않고 홈 내용을 표시하고 동일 DOM으로 hydration (${consent})`, async ({ page, request }) => {
  if (consent) await page.addInitScript((value) => localStorage.setItem('djc-analytics-consent', value), consent);
  await page.route('**/api/v1/analytics/events', (route) => route.fulfill({ status: 204 }));
  const raw = await request.get('/');
  expect(raw.status()).toBe(200);
  expect(await raw.text()).toContain('흩어진 개발자 채용공고');
  expect(await raw.text()).toContain('data-prerender-css');
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()); });
  await page.route('**/assets/index-*.js', async (route) => { await gate; await route.continue(); });
  await page.route('**/api/v1/jobs/search**', (route) => route.fulfill({ json: { content: [{ id: 7, title: 'Java 개발자', companyName: '테스트', techStacks: [] }] } }));
  try {
    await page.goto('/', { waitUntil: 'commit' });
    await expect(page.locator('#landing-title')).toBeVisible();
    await expect(page.locator('#hero-job-search')).toBeVisible();
    await page.evaluate(() => { window.initialHero = document.querySelector('#landing-title'); });
  } finally { release(); }
  await expect(page.locator('.job-card')).toHaveCount(1);
  expect(await page.evaluate(() => window.initialHero === document.querySelector('#landing-title'))).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(errors).toEqual([]);
});
}

test('robots와 내부 페이지는 올바른 HTTP 내용과 메타데이터 제공', async ({ request }) => {
  const robots = await request.get('/robots.txt');
  expect(robots.headers()['content-type']).toContain('text/plain');
  expect(await robots.text()).toContain('Sitemap:');
  const terms = await request.get('/terms');
  const html = await terms.text();
  expect(html).toContain('href="https://djc.itsdev.kr/terms"');
  expect(html).not.toContain('id="landing-title"');
  const member = await request.get('/member');
  expect(await member.text()).toContain('noindex,follow');
});
