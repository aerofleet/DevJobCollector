import { expect, test } from '@playwright/test';

const member = {
  id: 42,
  email: 'owner@example.com',
  name: '기업담당자',
  role: 'USER',
  profileStatus: 'ACTIVE',
};

const company = {
  companyId: 77,
  legalName: '데브잡스 주식회사',
  displayName: '데브잡스',
  businessNumberMasked: '123-**-67890',
  websiteUrl: 'https://example.com',
  companyStatus: 'PENDING_VERIFICATION',
  role: 'OWNER',
  membershipStatus: 'ACTIVE',
  verificationStatus: null,
  verificationRequestedAt: null,
  verificationReviewedAt: null,
};

const respond = (route, json, status = 200) => route.fulfill({
  status,
  contentType: 'application/json',
  body: JSON.stringify(json),
});

const expectNoHorizontalScroll = async (page) => {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
};

const installJourneyApi = async (page) => {
  const state = {
    companies: [],
    signupPayload: null,
    verificationPayload: null,
    companyPayload: null,
    evidencePayload: null,
  };

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (path === '/api/v1/auth/signup/personal' && method === 'POST') {
      state.signupPayload = request.postDataJSON();
      return respond(route, {
        email: state.signupPayload.email,
        verificationExpiresMinutes: 10,
        developmentVerificationCode: '123456',
      }, 201);
    }

    if (path === '/api/v1/auth/signup/personal/verify-email' && method === 'POST') {
      state.verificationPayload = request.postDataJSON();
      return respond(route, { accessToken: 'company-owner-token' });
    }

    if (path === '/api/v1/members/me' && method === 'GET') {
      return respond(route, member);
    }

    if (path === '/api/v1/companies/me' && method === 'GET') {
      return respond(route, state.companies);
    }

    if (path === '/api/v1/companies' && method === 'POST') {
      state.companyPayload = request.postDataJSON();
      state.companies = [{ ...company }];
      return respond(route, {
        companyId: company.companyId,
        legalName: company.legalName,
        displayName: company.displayName,
        businessNumberMasked: company.businessNumberMasked,
        websiteUrl: company.websiteUrl,
        status: company.companyStatus,
        ownerMembershipId: 91,
      }, 201);
    }

    if (path === `/api/v1/companies/${company.companyId}/verification-requests` && method === 'POST') {
      state.evidencePayload = request.postDataJSON();
      state.companies = [{
        ...company,
        verificationStatus: 'PENDING',
        verificationRequestedAt: '2026-09-10T03:00:00',
      }];
      return respond(route, {
        requestId: 101,
        companyId: company.companyId,
        status: 'PENDING',
        requestedAt: '2026-09-10T03:00:00',
      }, 201);
    }

    return route.fulfill({ status: 404, body: '' });
  });

  return state;
};

test('기업회원 가입 선택 후 기업 OWNER 등록과 인증 요청까지 완료한다', async ({ page }) => {
  const state = await installJourneyApi(page);

  await page.goto('/signup');
  await expect(page.getByRole('group', { name: '회원 유형' })).toBeVisible();
  const personalTab = page.getByRole('button', { name: '개인회원' });
  const companyTab = page.getByRole('button', { name: /기업회원/ });
  await expect(personalTab).toHaveAttribute('aria-pressed', 'true');
  await companyTab.click();
  await expect(page).toHaveURL(/\/signup$/);
  await expect(personalTab).toHaveAttribute('aria-pressed', 'false');
  await expect(companyTab).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('status')).toContainText('기업 정보 등록과 인증');
  await expect(page.getByRole('button', { name: '가입 후 기업 등록하기' })).toBeVisible();
  await expect(page.getByRole('main').getByRole('link', { name: '로그인' }))
    .toHaveAttribute('href', '/login?next=/company');
  expect(await page.evaluate(() => sessionStorage.getItem('postLoginNextPath'))).toBe('/company');
  await page.getByLabel('이름').fill(member.name);
  await page.getByLabel('이메일').fill(member.email);
  await page.getByLabel('비밀번호', { exact: true }).fill('Password1!');
  await page.getByLabel('비밀번호 확인').fill('Password1!');
  await page.getByLabel(/이용약관/).check();
  await page.getByLabel(/개인정보 처리방침/).check();
  await page.getByRole('button', { name: '가입 후 기업 등록하기' }).click();

  await expect(page.getByRole('heading', { name: '이메일을 확인해주세요' })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('로컬 개발용 인증 코드');
  await expect(page.getByLabel('이메일 인증 코드')).toHaveValue('123456');
  await page.getByRole('button', { name: '인증하고 시작하기' }).click();

  await expect(page).toHaveURL(/\/company$/);
  expect(await page.evaluate(() => sessionStorage.getItem('postLoginNextPath'))).toBeNull();
  expect(state.signupPayload).toEqual({
    email: member.email,
    name: member.name,
    password: 'Password1!',
    termsAccepted: true,
    privacyAccepted: true,
    turnstileToken: '',
  });
  expect(state.verificationPayload).toEqual({ email: member.email, code: '123456' });

  await expect(page.getByRole('heading', { name: '기업 정보 등록' })).toBeVisible();
  await page.getByLabel('법인명').fill(company.legalName);
  await page.getByLabel('서비스 표시명').fill(company.displayName);
  await page.getByLabel('사업자등록번호').fill('123-45-67890');
  await page.getByLabel(/기업 웹사이트/).fill(company.websiteUrl);
  await page.getByRole('button', { name: '기업 등록하기' }).click();

  await expect(page.getByRole('heading', { name: company.displayName })).toBeVisible();
  await expect(page.getByText(company.businessNumberMasked)).toBeVisible();
  await expect(page.getByRole('status')).toContainText('기업 등록이 완료되었습니다');
  expect(state.companyPayload).toEqual({
    legalName: company.legalName,
    displayName: company.displayName,
    businessNumber: '123-45-67890',
    websiteUrl: company.websiteUrl,
  });

  await page.getByLabel('증빙 문서 키').fill('company-verification/77/evidence.pdf');
  await page.getByRole('button', { name: '인증 요청 제출' }).click();

  await expect(page.getByRole('heading', { name: '관리자 검토 중' })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('기업 인증 요청을 접수했습니다');
  expect(state.evidencePayload).toEqual({
    method: 'BUSINESS_REGISTRATION_DOCUMENT',
    evidenceObjectKey: 'company-verification/77/evidence.pdf',
  });
  await expectNoHorizontalScroll(page);
});

test('기업회원 선택 의도는 소셜 로그인 callback 후 기업 등록으로 이어진다', async ({ page }) => {
  await installJourneyApi(page);

  await page.goto('/signup');
  await page.getByRole('button', { name: /기업회원/ }).click();
  await page.goto('/oauth/callback?token=company-social-token');

  await expect(page).toHaveURL(/\/company$/);
  await expect(page.getByRole('heading', { name: '기업 정보 등록' })).toBeVisible();
  expect(await page.evaluate(() => ({
    accessToken: localStorage.getItem('accessToken'),
    nextPath: sessionStorage.getItem('postLoginNextPath'),
  }))).toEqual({ accessToken: 'company-social-token', nextPath: null });
});

test('가입과 기업 조회 오류를 알리고 사용자가 다시 시도할 수 있다', async ({ page }) => {
  let companyLoadCount = 0;

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/v1/auth/signup/personal') {
      return respond(route, { detail: '이미 사용 중인 이메일입니다.' }, 409);
    }
    if (path === '/api/v1/members/me') {
      return respond(route, member);
    }
    if (path === '/api/v1/companies/me') {
      companyLoadCount += 1;
      return companyLoadCount === 1
        ? respond(route, { code: 'INTERNAL_ERROR' }, 500)
        : respond(route, []);
    }
    return route.fulfill({ status: 404, body: '' });
  });

  await page.goto('/signup');
  await page.getByLabel('이름').fill(member.name);
  await page.getByLabel('이메일').fill(member.email);
  await page.getByLabel('비밀번호', { exact: true }).fill('Password1!');
  await page.getByLabel('비밀번호 확인').fill('Password1!');
  await page.getByLabel(/이용약관/).check();
  await page.getByLabel(/개인정보 처리방침/).check();
  await page.getByRole('button', { name: '이메일로 가입하기' }).click();
  await expect(page.getByRole('alert')).toHaveText('이미 사용 중인 이메일입니다.');

  await page.evaluate(() => localStorage.setItem('accessToken', 'company-owner-token'));
  await page.goto('/company');
  await expect(page.getByRole('alert')).toContainText('기업 정보를 불러오지 못했습니다');
  await page.getByRole('button', { name: '다시 시도' }).click();
  await expect(page.getByRole('heading', { name: '기업 정보 등록' })).toBeVisible();
  expect(companyLoadCount).toBeGreaterThanOrEqual(2);
  await expectNoHorizontalScroll(page);
});

test('가입과 기업 등록 핵심 컨트롤은 키보드로 접근 가능하다', async ({ page }) => {
  await page.goto('/signup');

  const signupControls = [
    page.getByRole('button', { name: '개인회원' }),
    page.getByRole('button', { name: /기업회원/ }),
    page.getByRole('link', { name: 'Google로 계속' }),
    page.getByRole('link', { name: 'GitHub로 계속' }),
    page.getByLabel('이름'),
    page.getByLabel('이메일'),
    page.getByLabel('비밀번호', { exact: true }),
    page.getByLabel('비밀번호 확인'),
  ];

  for (const control of signupControls) {
    await control.focus();
    await expect(control).toBeFocused();
  }

  await page.addInitScript(() => localStorage.setItem('accessToken', 'company-owner-token'));
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/members/me') return respond(route, member);
    if (path === '/api/v1/companies/me') return respond(route, []);
    return route.fulfill({ status: 404, body: '' });
  });
  await page.goto('/company');

  const companyControls = [
    page.getByLabel('법인명'),
    page.getByLabel('서비스 표시명'),
    page.getByLabel('사업자등록번호'),
    page.getByLabel(/기업 웹사이트/),
    page.getByRole('button', { name: '기업 등록하기' }),
  ];
  for (const control of companyControls) {
    await control.focus();
    await expect(control).toBeFocused();
  }
  await expectNoHorizontalScroll(page);
});

test('모바일 가입과 기업 등록 컨트롤은 44px touch target을 제공한다', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-360', 'touch target 평가는 mobile-360에서 측정합니다.');
  await page.goto('/signup');

  const signupTargets = page.locator('.member-type-tabs button, .social-signup a, .signup-form input:not([type="checkbox"]), .signup-consents label, .signup-submit');
  const signupBoxes = await signupTargets.evaluateAll((elements) => elements.map((element) => {
    const rect = element.getBoundingClientRect();
    return { name: element.textContent.trim() || element.getAttribute('name'), width: rect.width, height: rect.height };
  }));
  for (const box of signupBoxes) {
    expect.soft(box.width, `${box.name} width`).toBeGreaterThanOrEqual(44);
    expect.soft(box.height, `${box.name} height`).toBeGreaterThanOrEqual(44);
  }

  await page.addInitScript(() => localStorage.setItem('accessToken', 'company-owner-token'));
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/members/me') return respond(route, member);
    if (path === '/api/v1/companies/me') return respond(route, []);
    return route.fulfill({ status: 404, body: '' });
  });
  await page.goto('/company');

  const companyTargets = page.locator('.company-form input, .company-form button');
  const companyBoxes = await companyTargets.evaluateAll((elements) => elements.map((element) => {
    const rect = element.getBoundingClientRect();
    return { name: element.textContent.trim() || element.getAttribute('name'), width: rect.width, height: rect.height };
  }));
  for (const box of companyBoxes) {
    expect.soft(box.width, `${box.name} width`).toBeGreaterThanOrEqual(44);
    expect.soft(box.height, `${box.name} height`).toBeGreaterThanOrEqual(44);
  }
});
