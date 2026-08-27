import { expect, test } from '@playwright/test';

const protectedRoutes = ['/member', '/my-devjobs', '/resumes', '/resume'];

const expectNoHorizontalScroll = async (page) => {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
};

for (const protectedRoute of protectedRoutes) {
  test(`${protectedRoute} 비인증 접근은 로그인 복귀 경로를 보존한다`, async ({ page }) => {
    await page.goto(protectedRoute);

    await expect(page).toHaveURL((url) => (
      url.pathname === '/login' && url.searchParams.get('next') === protectedRoute
    ));
    await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
    await expect(page.getByLabel('아이디 또는 이메일')).toBeVisible();
    await expect(page.getByLabel('비밀번호')).toBeVisible();
    await expect(page.getByRole('button', { name: '로그인' })).toBeVisible();
    await expectNoHorizontalScroll(page);
  });
}
