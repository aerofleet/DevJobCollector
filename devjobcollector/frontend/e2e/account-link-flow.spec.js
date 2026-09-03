import { expect, test } from '@playwright/test';

test('인증된 기존 계정은 이메일 충돌 후 Google 연결을 시작한다', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'existing-account-token');
  });

  await page.route('http://localhost:8080/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v1/auth/account-links/google/start') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: {
          'Access-Control-Allow-Origin': 'http://127.0.0.1:4174',
          'Access-Control-Allow-Credentials': 'true',
        },
        body: JSON.stringify({
          authorizationPath: '/oauth2/authorization/google',
          expiresInSeconds: 300,
        }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'text/html', body: '<p>OAuth authorization</p>' });
  });

  await page.goto('/oauth/callback?error=ACCOUNT_LINK_REQUIRED&provider=google');

  await expect(page.getByText('현재 로그인한 기존 계정에 소셜 계정을 연결할 수 있습니다.'))
    .toBeVisible();
  const linkButton = page.getByRole('button', { name: 'Google 계정 연결' });
  await expect(linkButton).toBeVisible();

  const startRequestPromise = page.waitForRequest((request) => (
    request.method() === 'POST'
    && new URL(request.url()).pathname === '/api/v1/auth/account-links/google/start'
  ));
  await linkButton.click();
  const startRequest = await startRequestPromise;

  expect(startRequest.headers().authorization).toBe('Bearer existing-account-token');
  await expect(page).toHaveURL('http://localhost:8080/oauth2/authorization/google');
});

test('기존 계정 인증이 없으면 연결 버튼을 노출하지 않는다', async ({ page }) => {
  await page.goto('/oauth/callback?error=ACCOUNT_LINK_REQUIRED&provider=google');

  await expect(page.getByText(
    '같은 이메일의 기존 계정으로 먼저 로그인해주세요. 로그인 후 소셜 계정을 연결할 수 있습니다.',
  )).toBeVisible();
  await expect(page.getByRole('button', { name: 'Google 계정 연결' })).toHaveCount(0);
});
