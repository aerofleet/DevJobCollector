import { expect, test } from '@playwright/test';

const now = new Date('2026-10-14T00:00:00Z');
const jwt = (payload) => `eyJhbGciOiJIUzI1NiJ9.${btoa(JSON.stringify(payload)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')}.test-signature`;
const validToken = jwt({ sub: '42', exp: now.getTime() / 1000 + 120 });

async function seed(page, token) {
  await page.clock.install({ time: now });
  await page.addInitScript((value) => {
    if (!sessionStorage.getItem('sessionFixtureSeeded')) {
      localStorage.setItem('accessToken', value);
      sessionStorage.setItem('sessionFixtureSeeded', 'true');
    }
  }, token);
  await page.route('**/api/v1/**', (route) => route.fulfill({ json:
    route.request().url().endsWith('/members/me')
      ? { id: 42, name: '테스트회원', email: 'member@example.com', role: 'USER' } : [],
  }));
}

for (const [name, token] of [
  ['expired', jwt({ exp: now.getTime() / 1000 - 1 })],
  ['malformed', 'invalid-token'],
  ['missing expiry', jwt({ sub: '42' })],
  ['string expiry', jwt({ exp: '4102444800' })],
]) {
  test(`${name}: 재접속 시 토큰 정리 및 비로그인 표시`, async ({ page }) => {
    await seed(page, token);
    await page.goto('/terms');
    await expect(page.locator('header').getByRole('link', { name: '로그인', exact: true }).first()).toBeAttached();
    expect(await page.evaluate(() => localStorage.getItem('accessToken'))).toBeNull();
    await expect(page).toHaveURL(/\/terms$/);
  });
}

test('공개 화면은 만료 시 로그인 표시만 해제하고 현재 페이지 유지', async ({ page }) => {
  await seed(page, validToken);
  await page.goto('/terms');
  if (page.viewportSize().width <= 768) await page.getByRole('button', { name: '메뉴 열기', exact: true }).click();
  await expect(page.locator('header').getByRole('button', { name: '로그아웃' }).first()).toBeVisible();
  await page.clock.fastForward(121000);
  await expect(page.locator('header').getByRole('link', { name: '로그인', exact: true }).first()).toBeAttached();
  expect(await page.evaluate(() => localStorage.getItem('accessToken'))).toBeNull();
  await expect(page).toHaveURL(/\/terms$/);
});

test('회원 화면은 사용 중 만료되면 복귀 경로를 보존해 로그인으로 이동', async ({ page }) => {
  await seed(page, validToken);
  await page.goto('/resumes');
  await expect(page.getByRole('heading', { name: '이력서 관리', exact: true })).toBeVisible();
  await page.clock.fastForward(121000);
  await expect(page).toHaveURL(/\/login\?next=%2Fresumes$/);
  await expect(page.getByRole('heading', { name: '이력서 관리', exact: true })).toHaveCount(0);
});

test('절전 후 탭 복귀 시 타이머가 실행되지 않아도 만료 확인', async ({ page }) => {
  await seed(page, validToken);
  await page.goto('/terms');
  await page.clock.setSystemTime(new Date(now.getTime() + 121000));
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.locator('header').getByRole('link', { name: '로그인', exact: true }).first()).toBeAttached();
  expect(await page.evaluate(() => localStorage.getItem('accessToken'))).toBeNull();
});

test('다른 탭의 로그아웃을 열린 회원 화면에도 반영', async ({ page, context }) => {
  await seed(page, validToken);
  await page.goto('/resumes');
  await expect(page.getByRole('heading', { name: '이력서 관리', exact: true })).toBeVisible();
  const other = await context.newPage();
  await other.goto('/terms');
  await other.evaluate(() => localStorage.removeItem('accessToken'));
  await expect(page).toHaveURL(/\/login\?next=%2Fresumes$/);
});

test('만료 토큰으로 회원 화면 재접속 시 API 호출 없이 로그인으로 이동', async ({ page }) => {
  await seed(page, jwt({ exp: now.getTime() / 1000 - 1 }));
  let requests = 0;
  page.on('request', (request) => { if (request.url().includes('/members/me')) requests += 1; });
  await page.goto('/resumes');
  await expect(page).toHaveURL(/\/login\?next=%2Fresumes$/);
  expect(requests).toBe(0);
});

test('서버에서 거절된 유효기간 내 토큰도 제거', async ({ page }) => {
  await seed(page, validToken);
  await page.route('**/api/v1/members/me', (route) => route.fulfill({ status: 401, json: {} }));
  await page.goto('/resumes');
  await expect(page).toHaveURL(/\/login\?next=%2Fresumes$/);
  expect(await page.evaluate(() => localStorage.getItem('accessToken'))).toBeNull();
});

test('다른 계정으로 로그인하면 이전 회원 캐시를 재사용하지 않음', async ({ page, context }) => {
  await seed(page, validToken);
  let requests = 0;
  await page.route('**/api/v1/members/me', (route) => {
    requests += 1;
    return route.fulfill({ json: { id: requests, name: `회원${requests}`, role: 'USER' } });
  });
  await page.goto('/resumes');
  await expect(page.getByRole('heading', { name: '이력서 관리', exact: true })).toBeVisible();
  const other = await context.newPage();
  await other.goto('/terms');
  await other.evaluate((token) => localStorage.setItem('accessToken', token), jwt({ sub: '43', exp: now.getTime() / 1000 + 300 }));
  await expect.poll(() => requests).toBe(2);
  await expect(page.getByRole('heading', { name: '이력서 관리', exact: true })).toBeVisible();
});
