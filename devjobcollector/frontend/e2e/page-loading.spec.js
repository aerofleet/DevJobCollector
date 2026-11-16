import { expect, test } from '@playwright/test';

test('페이지 코드가 늦게 도착하면 로딩 표시 후 페이지를 보여준다', async ({ page }) => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.addInitScript(() => localStorage.setItem('djc-analytics-consent', 'denied'));
  await page.route('**/api/v1/**', route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } }));
  await page.route('**/src/pages/LoginPage.jsx*', async route => { await gate; await route.continue(); });
  try {
    await page.goto('/');
    await page.locator('a[href="/login"]:visible').first().click();
    const loading = page.getByRole('status').filter({ hasText: '페이지를 불러오고 있어요' });
    await expect(loading).toBeVisible();
    await expect(loading).toHaveAttribute('aria-busy', 'true');
  } finally { release(); }
  await expect(page.getByRole('status').filter({ hasText: '페이지를 불러오고 있어요' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: /로그인/ }).first()).toBeVisible();
});
