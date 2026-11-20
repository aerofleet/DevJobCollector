import { expect, test } from '@playwright/test';

for (const [label, path] of [['로그인', '/login'], ['회원가입', '/signup']]) {
  test(`홈에서 ${label} 화면으로 바로 이동한다`, async ({ page }, testInfo) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    if (testInfo.project.name.startsWith('mobile') || testInfo.project.name.startsWith('tablet')) {
      await page.getByRole('button', { name: '메뉴 열기' }).click();
      await page.getByRole('dialog', { name: '모바일 메뉴' }).getByRole('link', { name: label, exact: true }).click();
    } else {
      await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: label, exact: true }).click();
    }
    await expect(page).toHaveURL(new RegExp(`${path}$`));
    await expect(page.getByRole('heading', { name: label, exact: true })).toBeVisible();
  });
}

test('로그인과 회원가입 화면 사이를 바로 이동한다', async ({ page }) => {
  await page.goto('/login');
  await page.locator('.login-signup-section').getByRole('link', { name: '회원가입' }).click();
  await expect(page).toHaveURL(/\/signup$/);
  await expect(page.getByRole('heading', { name: '회원가입', exact: true })).toBeVisible();
  await page.locator('.login-link').getByRole('link', { name: '로그인' }).click();
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole('heading', { name: '로그인', exact: true })).toBeVisible();
});

test('로그인 지연 로딩 동작 확인', async ({ page }, testInfo) => {
  page.on('request', request => { if (/LoginPage.*\.js/.test(request.url())) console.log('LOGIN_ASSET', new URL(request.url()).pathname); });
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  if (testInfo.project.name.startsWith('mobile') || testInfo.project.name.startsWith('tablet')) {
    await page.getByRole('button', { name: '메뉴 열기' }).click();
    await page.getByRole('dialog', { name: '모바일 메뉴' }).getByRole('link', { name: '로그인', exact: true }).click();
  } else {
    await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: '로그인', exact: true }).click();
  }
  await expect(page.getByRole('heading', { name: '로그인', exact: true })).toBeVisible();
});
