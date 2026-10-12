import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.addInitScript(() => {
    window.revealEvents = [];
    const animate = Element.prototype.animate;
    Element.prototype.animate = function (frames, options) {
      if (this.hasAttribute('data-motion-reveal')) window.revealEvents.push({ text: this.textContent, delay: options.delay, duration: options.duration, opacity: frames[0].opacity, step: this.dataset.motionStep, ready: document.readyState });
      return animate.call(this, frames, options);
    };
  });
  await page.route('**/api/v1/jobs/**', route => route.fulfill({ json: {
    content: Array.from({ length: 12 }, (_, i) => ({ id: i + 1, title: `개발자 ${i}`, companyName: '테스트', techStacks: [] })),
    totalElements: 12, totalPages: 1, candidateCount: 12,
  } }));
});

test('entrance is staggered; below-fold blocks reveal once on scroll', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('#landing-title')).toHaveAttribute('data-reveal-state', 'done');
  expect(await page.evaluate(() => window.revealEvents.some(e => e.delay > 0))).toBe(true);
  const firstScreen = await page.evaluate(() => window.revealEvents);
  expect(firstScreen.every(e => e.duration === 1400 && e.ready === 'complete')).toBe(true);
  expect(firstScreen.filter(e => e.step !== undefined).every(e => e.delay === Number(e.step) * 500)).toBe(true);
  expect(firstScreen.some(e => e.delay >= 500)).toBe(true);
  const target = page.locator('.login-cta');
  await expect(target).not.toHaveAttribute('data-reveal-state', /running|done/);
  await target.scrollIntoViewIfNeeded();
  await expect(target).toHaveAttribute('data-reveal-state', 'done');
  const count = await page.evaluate(() => window.revealEvents.filter(e => e.text.includes('마음에 드는 공고를 놓치지')).length);
  await page.evaluate(() => scrollTo(0, 0));
  await target.scrollIntoViewIfNeeded();
  expect(await page.evaluate(() => window.revealEvents.filter(e => e.text.includes('마음에 드는 공고를 놓치지')).length)).toBe(count);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('entrance waits for page load without blocking readable content', async ({ page }) => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.route('**/entry-ready-gate.svg', async route => {
    await gate;
    await route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>' });
  });
  await page.addInitScript(() => { window.loadProbe = new Image(); window.loadProbe.src = '/entry-ready-gate.svg'; });
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('#landing-title')).toBeVisible();
  expect(await page.evaluate(() => window.revealEvents.length)).toBe(0);
  release();
  await expect(page.locator('#landing-title')).toHaveAttribute('data-reveal-state', 'done');
  expect(await page.evaluate(() => window.revealEvents.every(e => e.ready === 'complete'))).toBe(true);
});

test('home defers other pages and failed route chunks have a retry screen', async ({ page }) => {
  const unrelated = [];
  page.on('request', request => { if (/\/(LoginPage|Resume|CompanyPage)\.jsx/.test(request.url())) unrelated.push(request.url()); });
  await page.goto('/');
  await expect(page.locator('#landing-title')).toBeVisible();
  expect(unrelated).toEqual([]);
  await page.route('**/src/pages/LoginPage.jsx*', route => route.abort());
  await page.goto('/login');
  await expect(page.getByRole('alert')).toContainText('화면을 불러오지 못했습니다.');
  await expect(page.getByRole('button', { name: '다시 불러오기' })).toBeVisible();
});

test('async cards, keyboard focus, runtime off and reduced motion remain visible', async ({ page }) => {
  await page.goto('/jobs');
  const card = page.locator('.job-card').last();
  await expect(card).toHaveCount(1);
  await card.focus();
  await expect(card).toHaveAttribute('data-reveal-state', 'done');
  await expect(card).toHaveCSS('opacity', '1');
  await page.evaluate(() => document.documentElement.dataset.motion = 'off');
  await expect.poll(() => page.locator('[data-reveal-state="running"]').count()).toBe(0);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(page.locator('#landing-title')).toHaveCSS('opacity', '1');
  expect(await page.evaluate(() => window.revealEvents.length)).toBe(0);
});
