import { expect, test } from '@playwright/test';

const fulfillJson = (route, status, body) => route.fulfill({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body),
});

test('미인증 관리자는 로그인으로 이동하고 일반 회원 토큰을 사용하지 않는다', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('accessToken', 'member-token'));
  const authorizationHeaders = [];
  page.on('request', (request) => authorizationHeaders.push(request.headers().authorization));
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 401, { code: 'ADMIN_SESSION_REQUIRED' }));
  await page.goto('/users');

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '관리자 로그인' })).toBeVisible();
  expect(authorizationHeaders.includes('Bearer member-token')).toBe(false);
});

test('인증 관리자는 대시보드 지표와 역할별 메뉴를 확인한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 200, {
    data: { id: 1, name: '운영 관리자', role: 'SUPER_ADMIN' },
  }));
  await page.route('**/api/v1/admin/dashboard/summary', (route) => fulfillJson(route, 200, {
    data: {
      asOf: '2026-09-24T09:00:00Z',
      timezone: 'Asia/Seoul',
      metrics: {
        totalUsers: { value: 1280, dataAvailable: true },
        weeklySignups: { value: 42, dataAvailable: true },
        pendingCompanies: { value: 7, dataAvailable: true },
        activeJobs: { value: 314, dataAvailable: true },
      },
      signupTrend: [
        { date: '2026-09-18', value: 3 },
        { date: '2026-09-19', value: 8 },
        { date: '2026-09-20', value: 5 },
        { date: '2026-09-21', value: 12 },
        { date: '2026-09-22', value: 4 },
        { date: '2026-09-23', value: 6 },
        { date: '2026-09-24', value: 9 },
      ],
      reviewQueue: { pendingCompanyVerifications: 7 },
    },
  }));
  await page.goto('/');

  await expect(page.getByRole('heading', { name: '오늘의 운영 현황' })).toBeVisible();
  await expect(page.getByText('1,280')).toBeVisible();
  await expect(page.getByLabel('최근 7일 신규 가입 추이')).toBeVisible();
  await expect(page.getByText('7건')).toBeVisible();
  await expect(page.getByRole('link', { name: '감사 기록' })).toBeVisible();
  await expect(page.getByRole('link', { name: '관리자 계정' })).toBeVisible();
});

test('모바일에서는 관리자 메뉴를 드로어로 제공한다', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-360', '모바일 전용 메뉴 평가입니다.');
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 200, {
    data: { id: 2, name: '운영자', role: 'OPERATOR' },
  }));
  await page.route('**/api/v1/admin/dashboard/summary', (route) => fulfillJson(route, 200, {
    data: { asOf: '2026-09-24T09:00:00Z', timezone: 'Asia/Seoul', metrics: {} },
  }));
  await page.goto('/');

  const menuButton = page.getByRole('button', { name: '관리 메뉴 열기' });
  await expect(menuButton).toBeVisible();
  await menuButton.click();
  await expect(page.getByRole('navigation', { name: '관리자 메뉴' })).toBeVisible();
  await expect(page.getByRole('link', { name: '감사 기록' })).toHaveCount(0);
});
