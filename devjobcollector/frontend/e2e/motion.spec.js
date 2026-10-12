import { test, expect } from '@playwright/test';

const job = { id: 42, title: '모션 검증 백엔드 개발자', companyName: '테스트 기업', techStacks: [], endDate: '2099-12-31', originalUrl: 'https://example.com/jobs/42' };
test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [job], totalPages: 1, totalElements: 1 } }));
  await page.route('**/api/v1/jobs/42*', route => route.fulfill({ json: job }));
});
test('filters, keyboard cards, responsive indicator and rollback', async ({ page }, info) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/jobs');
  const toggle = page.getByRole('button', { name: /^필터/ });
  if (await toggle.isVisible()) await toggle.click();
  const filter = page.getByRole('group', { name: '개발 직무' }).getByRole('button', { name: '백엔드', exact: true });
  await filter.focus(); await page.keyboard.press('Enter');
  await expect(filter).toHaveAttribute('aria-pressed', 'true');
  await expect(page).toHaveURL(/jobCategory=backend/);
  const indicator = page.locator('.im-filter-indicator');
  await expect(indicator).toBeVisible();
  await expect.poll(async () => {
    const a = await indicator.boundingBox(), b = await filter.boundingBox();
    return Math.abs(a.x-b.x)+Math.abs(a.y-b.y)+Math.abs(a.width-b.width);
  }).toBeLessThan(2);
  const card = page.locator('.job-card').first();
  await card.focus();
  await expect(card).toBeFocused();
  await expect.poll(() => card.evaluate(e => getComputedStyle(e).transform)).not.toBe('none');
  await page.evaluate(() => document.documentElement.dataset.motion = 'off');
  await expect(card).toHaveCSS('transform', 'none');
  await expect(card).toHaveCSS('transition-duration', '0s');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.codex/motion-${info.project.name}.png`, fullPage: true });
  await page.keyboard.press('Enter'); await expect(page).toHaveURL(/\/job\/42$/);
  expect(errors).toEqual([]);
});
test('reduced motion and loading preserve readable content', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  let release; const gate = new Promise(resolve => { release = resolve; });
  await page.route('**/api/v1/jobs/search**', async route => { await gate; await route.fulfill({ json: { content: [job], totalPages: 1, totalElements: 1 } }); });
  await page.goto('/jobs');
  await expect(page.getByRole('status', { name: '공고 불러오는 중' })).toBeVisible();
  await expect(page.locator('.im-skeleton').first()).toHaveCSS('animation-name', 'none');
  release();
  const card = page.locator('.job-card').first(); await expect(card).toBeVisible();
  await card.focus(); await expect(card).toHaveCSS('transform', 'none');
  await expect(page.locator('.im-content')).toHaveCSS('animation-name', 'none');
});
test('mobile modal traps focus, closes with Escape and restores trigger', async ({ page }) => {
  test.skip(page.viewportSize().width > 768, 'Mobile menu only');
  await page.goto('/jobs');
  const trigger = page.getByRole('button', { name: '메뉴 열기' });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: '모바일 메뉴' });
  await expect(dialog).toBeVisible();
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab');
    expect(await dialog.evaluate(e => e.contains(document.activeElement) || document.activeElement === document.body)).toBe(true);
  }
  await page.keyboard.press('Escape'); await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
});
test('bookmark toast reports success and dismisses without navigation', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('accessToken', 'motion-fixture'));
  await page.route('**/api/v1/members/**', route => route.fulfill({ json: {} }));
  await page.goto('/job/42');
  await page.getByRole('button', { name: '공고 저장', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('저장한 공고에 추가했습니다.');
  await page.getByRole('button', { name: '알림 닫기' }).click();
  await expect(page.getByRole('status')).toBeEmpty();
  await expect(page).toHaveURL(/\/job\/42$/);
});
