import { test, expect } from '@playwright/test';

test('hero entrance uses distinct transforms and retains usable search', async ({ page }, testInfo) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.addInitScript(() => {
    window.heroFrames = {};
    const animate = Element.prototype.animate;
    Element.prototype.animate = function (frames, options) {
      if (this.dataset.motionEnter) window.heroFrames[this.dataset.motionEnter] = frames;
      return animate.call(this, frames, options);
    };
  });
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [], totalPages: 0 } }));
  await page.goto('/');
  const visual = page.locator('.hero-visual');
  await visual.scrollIntoViewIfNeeded();
  await expect(visual).toHaveAttribute('data-reveal-state', 'done');
  const frames = await page.evaluate(() => window.heroFrames);
  expect(frames.headline[0].translate).toBe('-48px 18px');
  expect(frames.search[0].scale).toBe('0.94');
  expect(frames.visual[0].rotate).toBe('-8deg');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.evaluate(() => scrollTo(0, 0));
  await page.screenshot({ path: `.codex/hero-${testInfo.project.name}.png`, fullPage: true });
  await page.getByRole('searchbox', { name: '채용공고 검색', exact: true }).fill('Java');
  await page.getByRole('button', { name: '공고 찾기', exact: true }).click();
  await expect(page).toHaveURL(/\/jobs\?keyword=Java/);
});
