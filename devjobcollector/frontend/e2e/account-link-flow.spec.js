import { expect, test } from '@playwright/test';

test('인증된 기존 계정은 오류 화면 없이 Google 연결을 자동 시작한다', async ({ page }) => {
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

  const startRequestPromise = page.waitForRequest((request) => (
    request.method() === 'POST'
    && new URL(request.url()).pathname === '/api/v1/auth/account-links/google/start'
  ));
  await page.goto('/oauth/callback?error=ACCOUNT_LINK_REQUIRED&provider=google');
  const startRequest = await startRequestPromise;

  expect(startRequest.headers().authorization).toBe('Bearer existing-account-token');
  await expect(page.getByText('현재 로그인한 기존 계정에 소셜 계정을 연결할 수 있습니다.'))
    .toHaveCount(0);
  await expect(page).toHaveURL('http://localhost:8080/oauth2/authorization/google');
});

test('기존 계정 인증이 없으면 연결 버튼을 노출하지 않는다', async ({ page }) => {
  await page.goto('/oauth/callback?error=ACCOUNT_LINK_REQUIRED&provider=google');

  await expect(page.getByText(
    '같은 이메일의 기존 가입 방식으로 로그인해주세요. 인증 후 계정 연결을 자동으로 계속합니다.',
  )).toBeVisible();
  await expect(page.getByRole('button', { name: 'Google 계정 연결' })).toHaveCount(0);
});

test('Google 충돌 후 GitHub 재인증을 마치면 Google 연결을 자동 재개한다', async ({ page }) => {
  await page.route('http://localhost:8080/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/oauth2/authorization/github') {
      await route.fulfill({
        status: 302,
        headers: {
          Location: 'http://127.0.0.1:4174/oauth/callback?token=fresh-github-token',
        },
      });
      return;
    }
    if (path === '/api/v1/auth/account-links/google/start') {
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
    await route.fulfill({
      status: 200,
      contentType: 'text/html',
      body: '<p>Google OAuth authorization</p>',
    });
  });

  await page.goto('/oauth/callback?error=ACCOUNT_LINK_REQUIRED&provider=google');
  const startRequestPromise = page.waitForRequest((request) => (
    request.method() === 'POST'
    && new URL(request.url()).pathname === '/api/v1/auth/account-links/google/start'
  ));
  await page.getByTitle('github').click();
  const startRequest = await startRequestPromise;

  expect(startRequest.headers().authorization).toBe('Bearer fresh-github-token');
  await expect(page).toHaveURL('http://localhost:8080/oauth2/authorization/google');
});

test('Google 연결 성공 callback은 연결 상태를 폐기하고 회원 화면으로 이동한다', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('pendingAccountLinkProvider', JSON.stringify({
      provider: 'google',
      createdAt: Date.now(),
    }));
    sessionStorage.setItem('accountLinkInProgressProvider', 'google');
  });

  await page.goto('/oauth/callback?token=linked-account-token');

  await expect(page).toHaveURL(/\/member$/);
  const authState = await page.evaluate(() => ({
    accessToken: localStorage.getItem('accessToken'),
    pendingProvider: sessionStorage.getItem('pendingAccountLinkProvider'),
    inProgressProvider: sessionStorage.getItem('accountLinkInProgressProvider'),
  }));
  expect(authState).toEqual({
    accessToken: 'linked-account-token',
    pendingProvider: null,
    inProgressProvider: null,
  });
});
