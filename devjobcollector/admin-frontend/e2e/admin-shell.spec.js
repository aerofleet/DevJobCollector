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
  await expect(page.getByRole('dialog', { name: '회원 상세' })).toBeVisible();
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
  let active = true;
  let endDate = '2026-12-31';
  const job = () => ({ id: 9, title: '백엔드 개발자', companyName: '데브잡스',
    moderationStatus, version, active, sourcePlatform: 'SARAMIN', endDate,
    originalUrl: 'https://example.com/jobs/9' });
  await page.route('**/api/v1/admin/jobs?*', (route) => fulfillJson(route, 200, {
    data: { content: [job()], totalElements: 1, totalPages: 1 },
  }));
  await page.route('**/api/v1/admin/jobs/9', (route) => fulfillJson(route, 200, { data: job() }));
  await page.route('**/api/v1/admin/jobs/9/status', async (route) => {
    const body = route.request().postDataJSON();
    if (version === 0) expect(body).toEqual({
      status: 'HIDDEN', expectedVersion: 0, reason: '중복 공고',
    });
    if (version === 1) {
      expect(body).toEqual({ status: 'CLOSED', expectedVersion: 1, reason: '채용 종료' });
      active = false;
    }
    if (version === 2) {
      expect(body).toEqual({ status: 'ACTIVE', expectedVersion: 2,
        reason: '재등록 확인', newEndDate: '2027-01-31' });
      active = true;
      endDate = body.newEndDate;
    }
    moderationStatus = body.status;
    version += 1;
    await fulfillJson(route, 200, { data: job() });
  });

  await page.goto('/jobs');
  await expect(page.getByText('백엔드 개발자')).toBeVisible();
  await page.getByRole('button', { name: '상세' }).click();
  await expect(page.getByRole('dialog', { name: '공고 상세' })).toBeVisible();
  await expect(page.getByRole('button', { name: '공고 숨김' })).toBeEnabled();
  await expect(page.getByRole('button', { name: '강제 마감' })).toBeEnabled();
  await page.getByRole('button', { name: '공고 숨김' }).click();
  await expect(page.getByRole('group', { name: '공고 상태 변경 확인' })).toBeVisible();
  await expect(page.getByRole('button', { name: '변경 확정' })).toBeDisabled();
  await page.getByLabel('변경 사유').fill('중복 공고');
  await page.getByRole('button', { name: '변경 확정' }).click();
  await expect(page.getByRole('button', { name: '재활성' })).toBeVisible();
  await page.getByRole('button', { name: '강제 마감' }).click();
  await page.getByLabel('변경 사유').fill('채용 종료');
  await page.getByRole('button', { name: '변경 확정' }).click();
  await page.getByRole('button', { name: '재활성' }).click();
  await expect(page.getByRole('button', { name: '변경 확정' })).toBeDisabled();
  await page.getByLabel('변경 사유').fill('재등록 확인');
  await expect(page.getByRole('button', { name: '변경 확정' })).toBeDisabled();
  await page.getByLabel('새 마감일 (필수)').fill('2027-01-31');
  await page.getByRole('button', { name: '변경 확정' }).click();
  await expect(page.getByText('활성', { exact: true })).toBeVisible();
});

test('기업 상세에서 인증 요청을 조회하되 증빙 키와 승인 버튼은 표시하지 않는다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 200, {
    data: { id: 1, name: '관리자', role: 'ADMIN' },
  }));
  await page.route('**/api/v1/admin/companies?*', (route) => fulfillJson(route, 200, {
    data: { content: [{ id: 4, displayName: '데브잡스', legalName: '데브잡스 주식회사',
      businessNumberMasked: '123-**-*****', status: 'PENDING_VERIFICATION' }],
    totalElements: 1, totalPages: 1 },
  }));
  await page.route('**/api/v1/admin/companies/4', (route) => fulfillJson(route, 200, {
    data: { company: { id: 4, displayName: '데브잡스', legalName: '데브잡스 주식회사',
      businessNumberMasked: '123-**-*****', status: 'PENDING_VERIFICATION' },
    latestRequest: { id: 8, method: 'BUSINESS_REGISTRATION_DOCUMENT', status: 'PENDING',
      requestedAt: '2026-09-29T10:00:00' } },
  }));
  await page.goto('/companies');
  await page.getByRole('button', { name: '상세' }).click();
  await expect(page.getByRole('dialog', { name: '기업 상세' })).toContainText('PENDING');
  await expect(page.getByRole('button', { name: '승인' })).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: '기업 상세' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '상세' })).toBeFocused();
  await page.getByRole('button', { name: '상세' }).click();
  await page.locator('.detail-modal-overlay').click({ position: { x: 5, y: 5 } });
  await expect(page.getByRole('dialog', { name: '기업 상세' })).toHaveCount(0);
});

test('최고 관리자는 감사 기록을 검색하고 다른 역할은 접근할 수 없다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 200, {
    data: { id: 1, name: '최고 관리자', role: 'SUPER_ADMIN' },
  }));
  await page.route('**/api/v1/admin/audit?*', (route) => fulfillJson(route, 200, {
    data: { content: [{ id: 1, occurredAt: '2026-09-29T10:00:00', actorAdminId: 1,
      action: 'USER_STATUS_CHANGED', targetType: 'USER', targetId: '7',
      result: 'SUCCESS', reason: '운영 정책 위반', requestId: 'request-1' }],
    totalElements: 1, totalPages: 1 },
  }));
  await page.goto('/audit');
  await expect(page.getByText('USER_STATUS_CHANGED')).toBeVisible();
  await page.getByLabel('대상 ID').fill('7');
  await page.getByRole('button', { name: '검색' }).click();
  await expect(page.getByText('운영 정책 위반')).toBeVisible();
});

test('최고 관리자는 MFA 계정을 만들고 역할 변경 시 확인 절차를 거친다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => fulfillJson(route, 200, {
    data: { id: 1, name: '최고 관리자', role: 'SUPER_ADMIN' },
  }));
  let account = null;
  await page.route('**/api/v1/admin/admins?*', (route) => fulfillJson(route, 200, {
    data: { content: account ? [account] : [], totalElements: account ? 1 : 0, totalPages: 1 },
  }));
  await page.route('**/api/v1/admin/admins', async (route) => {
    const body = route.request().postDataJSON();
    expect(body.password.length).toBeGreaterThanOrEqual(16);
    expect(body.mfaSecret).toMatch(/^[A-Z2-7]{32}$/);
    account = { id: 2, email: body.email, name: body.name, role: body.role,
      status: 'ACTIVE', mfaConfigured: true, version: 0 };
    await fulfillJson(route, 200, { data: account });
  });
  await page.route('**/api/v1/admin/admins/2', (route) => fulfillJson(route, 200, { data: account }));
  await page.route('**/api/v1/admin/admins/2/role', async (route) => {
    expect(route.request().postDataJSON()).toEqual({
      role: 'REVIEWER', expectedVersion: 0, reason: '업무 변경',
    });
    account = { ...account, role: 'REVIEWER', version: 1 };
    await fulfillJson(route, 200, { data: account });
  });

  await page.goto('/admins');
  await page.getByRole('button', { name: '관리자 추가' }).click();
  const createPanel = page.getByRole('region', { name: '관리자 추가' });
  await createPanel.getByLabel('이메일', { exact: true }).fill('new-admin@example.com');
  await createPanel.getByLabel('이름', { exact: true }).fill('신규 관리자');
  await page.getByRole('button', { name: '보안값 생성' }).click();
  await page.getByRole('button', { name: '계정 생성' }).click();
  await expect(page.getByText('비밀번호와 MFA 비밀키를 안전한 채널로 전달한 뒤')).toBeVisible();
  await page.getByRole('region', { name: '관리자 추가' }).getByRole('button', { name: '닫기' }).click();
  await expect(page.getByRole('cell', { name: 'new-admin@example.com' })).toBeVisible();
  await page.getByRole('button', { name: '상세' }).click();
  await expect(page.getByRole('dialog', { name: '관리자 상세' })).toBeVisible();
  await page.getByLabel('변경 사유').fill('업무 변경');
  await page.getByLabel('새 역할').selectOption('REVIEWER');
  await page.getByRole('button', { name: '역할 변경' }).click();
  await expect(page.getByRole('group', { name: '관리자 계정 변경 확인' })).toBeVisible();
  await page.getByRole('button', { name: '변경 확정' }).click();
  await expect(page.getByRole('dialog', { name: '관리자 상세' })).toContainText('심사 담당');
});
