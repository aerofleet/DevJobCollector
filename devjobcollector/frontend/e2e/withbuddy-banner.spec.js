import { expect, test } from '@playwright/test';

const token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.signature';
const company = { companyId: 77, displayName: '테스트 기업', legalName: '테스트 기업', companyStatus: 'VERIFIED', role: 'OWNER', membershipStatus: 'ACTIVE' };
const name = '신입사원 온보딩을 돕는 사내 문서 기반 AI 비서 WithBuddy 알아보기';

async function setup(page, companies, consent = false) {
  await page.addInitScript(({ token, consent }) => {
    localStorage.setItem('accessToken', token);
    localStorage.setItem('djc-analytics-consent', consent ? 'granted' : 'denied');
    localStorage.setItem('djc-google-analytics-consent', consent ? 'granted' : 'denied');
  }, { token, consent });
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    const data = path.endsWith('/members/me') ? { id: 1, name: '담당자', role: 'USER', profileStatus: 'ACTIVE' }
      : path.endsWith('/companies/me') ? companies : path.endsWith('/jobs') ? { content: [], totalPages: 1 } : [];
    return route.fulfill({ json: data });
  });
}

test('활성 기업에 배너와 반응형 대안 및 안전한 링크를 제공한다', async ({ page }, testInfo) => {
  await setup(page, [company], true);
  await page.goto('/company');
  const link = page.getByRole('link', { name });
  await expect(link).toBeVisible();
  await expect(link).toHaveAttribute('target', '_blank');
  await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  const href = new URL(await link.getAttribute('href'));
  expect(href.origin + href.pathname).toBe('https://withbuddy.ai.kr/');
  expect(Object.fromEntries(href.searchParams)).toEqual({ utm_source: 'devjobs', utm_medium: 'banner', utm_campaign: 'company_onboarding' });
  await expect(link.locator('img')).toHaveAttribute('alt', name);
  await link.focus();
  await expect(link).toBeFocused();
  expect(await link.evaluate(el => getComputedStyle(el).outlineStyle)).not.toBe('none');
  if (testInfo.project.name === 'mobile-360') await expect(link.locator('.withbuddy-banner-mobile')).toBeVisible();
  else await expect(link.locator('img')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.evaluate(() => { window.dataLayer = []; });
  await page.context().route('https://withbuddy.ai.kr/**', route => route.fulfill({ contentType: 'text/html', body: '<title>WithBuddy</title>' }));
  const popupPromise = page.waitForEvent('popup');
  await link.click();
  const popup = await popupPromise;
  await expect(popup).toHaveURL(/utm_source=devjobs/);
  await expect.poll(() => page.evaluate(() => window.dataLayer.some(e => e[0] === 'event' && e[1] === 'withbuddy_banner_click'))).toBe(true);
  await popup.close();
});

test('개인회원과 일반 공고에는 배너를 표시하지 않는다', async ({ page }) => {
  await setup(page, []);
  await page.goto('/company');
  await expect(page.getByRole('heading', { name: '기업 정보 등록' })).toBeVisible();
  await expect(page.getByRole('link', { name })).toHaveCount(0);
  await page.goto('/jobs');
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});

test('비활성 소속에는 배너를 표시하지 않는다', async ({ page }) => {
  await setup(page, [{ ...company, membershipStatus: 'LEFT' }]);
  await page.goto('/company');
  await expect(page.getByText('LEFT', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});

test('분석 동의가 없으면 클릭 이벤트를 전송하지 않는다', async ({ page }) => {
  await setup(page, [company]);
  await page.goto('/company');
  await page.context().route('https://withbuddy.ai.kr/**', route => route.fulfill({ body: 'WithBuddy' }));
  await page.evaluate(() => { window.dataLayer = []; });
  const popupPromise = page.waitForEvent('popup');
  await page.getByRole('link', { name }).click();
  const popup = await popupPromise;
  await expect(popup).toHaveURL(/utm_source=devjobs/);
  expect(await page.evaluate(() => window.dataLayer.some(e => e[0] === 'event' && e[1] === 'withbuddy_banner_click'))).toBe(false);
  await popup.close();
});

test('비로그인 사용자는 기업 배너에 접근할 수 없다', async ({ page }) => {
  await page.goto('/company');
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});

test('홈에서 활성 인증 기업에만 배너가 표시된다', async ({ page }) => {
  await setup(page, [company]);
  await page.goto('/');
  const link = page.getByRole('link', { name });
  await expect(link).toBeVisible();
  await expect(link).toHaveAttribute('target', '_blank');
  await expect(link).toHaveAttribute('href', /utm_campaign=company_onboarding/);
  await expect(page.locator('.home-company-banner + .login-cta')).toHaveCount(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('홈에서 비로그인 사용자에게 배너를 표시하고 개인회원에게는 표시하지 않는다', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('link', { name })).toBeVisible();
  await expect(page.locator('.home-company-banner + .login-cta')).toHaveCount(1);
  await setup(page, []);
  await page.goto('/');
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});

test('홈에서 기업 조회 실패 및 비활성 소속은 배너를 표시하지 않는다', async ({ page }) => {
  await setup(page, [{ ...company, membershipStatus: 'LEFT' }]);
  const request = page.waitForResponse(response => response.url().includes('/companies/me'));
  await page.goto('/');
  await request;
  await expect(page.getByRole('link', { name })).toHaveCount(0);
  await page.route('**/api/v1/companies/me', route => route.fulfill({ status: 500, json: {} }));
  const failed = page.waitForResponse(response => response.url().includes('/companies/me'));
  await page.reload();
  await failed;
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});

test('홈 배너 스크롤 모션과 동작 줄이기 설정을 따른다', async ({ page }) => {
  await page.goto('/');
  const banner = page.locator('.home-company-banner');
  await expect(banner).toHaveAttribute('data-motion-reveal', '');
  await banner.scrollIntoViewIfNeeded();
  await expect(banner).toHaveAttribute('data-reveal-state', /running|done/);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.reload();
  await expect(banner).toHaveAttribute('data-reveal-state', 'done');
  await expect(page.getByRole('link', { name })).toBeVisible();
});
