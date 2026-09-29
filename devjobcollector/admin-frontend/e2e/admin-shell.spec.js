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

test('회원 목록에서 사유를 입력해 정지하고 최신 상태를 다시 표시한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 200, {
    data: { id: 1, name: '관리자', role: 'ADMIN' },
  }));
  let status = 'ACTIVE';
  let version = 0;
  await page.route('**/api/v1/admin/users?*', (route) => fulfillJson(route, 200, {
    data: { content: [{ id: 7, name: '회원', email: 'member@example.com', status,
      createdAt: '2026-09-01T00:00:00', version }], totalElements: 1, totalPages: 1 },
  }));
  await page.route('**/api/v1/admin/users/7', (route) => fulfillJson(route, 200, {
    data: { id: 7, name: '회원', email: 'member@example.com', status, version, provider: 'LOCAL' },
  }));
  await page.route('**/api/v1/admin/users/7/status', async (route) => {
    const body = route.request().postDataJSON();
    expect(body).toEqual({ status: 'SUSPENDED', expectedVersion: 0, reason: '운영 정책 위반' });
    status = 'SUSPENDED';
    version = 1;
    await fulfillJson(route, 200, { data: {
      id: 7, name: '회원', email: 'member@example.com', status, version, provider: 'LOCAL',
    } });
  });
  await page.goto('/users');
  await expect(page.getByText('member@example.com')).toBeVisible();
  await page.getByRole('button', { name: '상세' }).click();
  await page.getByLabel('변경 사유').fill('운영 정책 위반');
  await page.getByRole('button', { name: '회원 정지' }).click();
  await expect(page.getByRole('group', { name: '상태 변경 확인' })).toBeVisible();
  await page.getByRole('button', { name: '변경 확정' }).click();
  await expect(page.getByRole('button', { name: '정지 해제' })).toBeVisible();
});

test('공고 목록에서 사유를 확인하고 숨김 처리한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 200, {
    data: { id: 1, name: '관리자', role: 'ADMIN' },
  }));
  let moderationStatus = 'ACTIVE';
  let version = 0;
  const job = () => ({ id: 9, title: '백엔드 개발자', companyName: '데브잡스',
    moderationStatus, version, active: true, sourcePlatform: 'SARAMIN', endDate: '2026-12-31' });
  await page.route('**/api/v1/admin/jobs?*', (route) => fulfillJson(route, 200, {
    data: { content: [job()], totalElements: 1, totalPages: 1 },
  }));
  await page.route('**/api/v1/admin/jobs/9', (route) => fulfillJson(route, 200, { data: job() }));
  await page.route('**/api/v1/admin/jobs/9/status', async (route) => {
    expect(route.request().postDataJSON()).toEqual({
      status: 'HIDDEN', expectedVersion: 0, reason: '중복 공고',
    });
    moderationStatus = 'HIDDEN';
    version = 1;
    await fulfillJson(route, 200, { data: job() });
  });

  await page.goto('/jobs');
  await expect(page.getByText('백엔드 개발자')).toBeVisible();
  await page.getByRole('button', { name: '상세' }).click();
  await page.getByLabel('변경 사유').fill('중복 공고');
  await page.getByRole('button', { name: '숨김' }).click();
  await expect(page.getByRole('group', { name: '공고 상태 변경 확인' })).toBeVisible();
  await page.getByRole('button', { name: '변경 확정' }).click();
  await expect(page.getByRole('button', { name: '복구' })).toBeVisible();
});
