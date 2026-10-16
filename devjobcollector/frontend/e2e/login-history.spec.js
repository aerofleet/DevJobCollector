import { expect, test } from '@playwright/test';

const token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiIsImV4cCI6NDEwMjQ0NDgwMH0.test-signature';
const job = { id: 9564, title: '회귀 테스트 공고', companyName: '테스트 기업', techStacks: [], endDate: '2099-12-31' };

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({ json: path === '/api/v1/jobs/9564' ? job : path.endsWith('/members/me') ? { id: 42, name: '회원', role: 'USER' } : { content: [], totalElements: 0, totalPages: 0, last: true } });
  });
});

for (const method of ['browser', 'button']) {
  test(`소셜 로그인 후 ${method} 뒤로가기는 로그인 기록을 건너뛴다`, async ({ page }) => {
    await page.goto('/job/9564');
    await page.getByRole('button', { name: /저장/ }).click();
    await expect(page).toHaveURL(/login\?next=/);
    await page.route('http://localhost:8080/oauth2/authorization/google', route => route.fulfill({
      status: 302, headers: { Location: 'http://127.0.0.1:4174/oauth/callback?token=' + token },
    }));
    await page.getByTitle('google').click();
    await expect(page).toHaveURL('/job/9564');
    if (method === 'browser') {
      await page.goBack();
      await expect(page).toHaveURL('/job/9564');
      await expect(page.getByRole('heading', { name: '회귀 테스트 공고' })).toBeVisible();
    }
    await page.getByRole('button', { name: '← 돌아가기' }).click();
    await expect(page).toHaveURL('/jobs');
    expect(await page.evaluate(() => localStorage.getItem('accessToken'))).toBe(token);
  });
}

test('직접 진입한 상세의 돌아가기는 목록으로 이동한다', async ({ page }) => {
  await page.goto('/job/9564');
  await page.getByRole('button', { name: '← 돌아가기' }).click();
  await expect(page).toHaveURL('/jobs');
});

test('로그인한 사용자의 로그인 URL 직접 진입은 next로 이동한다', async ({ page }) => {
  await page.addInitScript(value => localStorage.setItem('accessToken', value), token);
  await page.goto('/login?next=%2Fjob%2F9564');
  await expect(page).toHaveURL('/job/9564');
});

test('비로그인 사용자는 로그인 화면을 유지한다', async ({ page }) => {
  await page.goto('/login?next=%2Fjob%2F9564');
  await expect(page.getByRole('heading', { name: '로그인', exact: true })).toBeVisible();
});
