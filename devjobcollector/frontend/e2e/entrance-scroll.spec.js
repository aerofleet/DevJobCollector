import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.addInitScript(() => {
    window.revealEvents = [];
    const animate = Element.prototype.animate;
    Element.prototype.animate = function (frames, options) {
      if (this.hasAttribute('data-motion-reveal')) window.revealEvents.push({ text: this.textContent, delay: options.delay });
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
