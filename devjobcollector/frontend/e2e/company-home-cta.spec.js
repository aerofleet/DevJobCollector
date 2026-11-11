import { expect, test } from '@playwright/test';

test('비로그인 홈에서 기업 인증과 무료 등록을 안내하고 로그인으로 이동한다', async ({ page }) => {
  await page.route('**/api/v1/auth/login', route => route.fulfill({ json: { accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.signature' } }));
  await page.route('**/api/v1/members/me', route => route.fulfill({ json: { id: 1, name: '담당자', role: 'USER', profileStatus: 'ACTIVE' } }));
  await page.route('**/api/v1/companies/me', route => route.fulfill({ json: [] }));
  await page.goto('/');
  const section = page.getByRole('region', { name: '채용 공고를 무료로 등록하세요' });
  await expect(section).toBeVisible();
  await expect(section).toContainText('기업 등록과 인증');
  await expect(section).toContainText('검수 승인 후');
  const link = section.getByRole('link', { name: '로그인하고 무료 공고 등록' });
  await expect(link).toHaveAttribute('href', '/login?next=%2Fcompany');
  await link.focus();
  await expect(link).toBeFocused();
  expect(await link.evaluate(element => getComputedStyle(element).outlineStyle)).not.toBe('none');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/login\?next=%2Fcompany/);
  await page.locator('#identifier').fill('company@example.com');
  await page.locator('#password').fill('example-password');
  await page.locator('.login-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/company$/);
});

test('로그인 상태 홈에는 비로그인 기업 안내를 표시하지 않는다', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.signature'));
  await page.route('**/api/v1/**', route => route.fulfill({ json: [] }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '채용 공고를 무료로 등록하세요' })).toHaveCount(0);
});
