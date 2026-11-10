import { test, expect } from '@playwright/test';

async function setup(page, consent) {
  if (consent) await page.addInitScript((value) => localStorage.setItem('djc-analytics-consent', value), consent);
  const events = [];
  await page.route('**/api/v1/**', (route) => {
    if (route.request().url().endsWith('/analytics/events')) { events.push(route.request().postDataJSON()); return route.fulfill({ status: 204 }); }
    if (/\/jobs\/7(?:\?|$)/.test(route.request().url())) return route.fulfill({ json: { id: 7, title: 'Java 개발자', companyName: '테스트', originalUrl: 'https://example.com/job/7', techStacks: [], endDate: '2099-01-01' } });
    return route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0 } });
  });
  return events;
}
test('동의 전 식별자와 이벤트를 만들지 않는다', async ({ page }) => {
  const events = await setup(page);
  await page.goto('/terms');
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  expect(events).toHaveLength(0);
  expect(await page.evaluate(() => localStorage.getItem('djc-analytics-visitor'))).toBeNull();
  await expect(page.locator('script[src*="googletagmanager.com/gtag/js"]')).toHaveCount(0);
  await expect(page.locator('script[src*="clarity.ms/tag/"]')).toHaveCount(0);
});

test('Clarity는 별도 동의 후 로드되고 철회 후에는 다시 로드되지 않는다', async ({ page }) => {
  await setup(page, 'granted');
  let requests = 0;
  await page.route('https://www.clarity.ms/tag/**', route => { requests++; return route.fulfill({ status: 200, contentType: 'text/javascript', body: '' }); });
  await page.goto('/terms');
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  expect(requests).toBe(0);
  await page.getByRole('button', { name: '이용 통계 허용 중' }).click();
  await page.getByRole('button', { name: '통계 수집 허용' }).click();
  await expect.poll(() => requests).toBe(1);
  await expect(page.locator('script[src="https://www.clarity.ms/tag/zbh7shexgg?ref=bwt"]')).toHaveCount(1);
  expect(await page.evaluate(() => window.clarity.q[0])).toEqual(['consentv2', { ad_Storage: 'denied', analytics_Storage: 'granted' }]);
  await expect(page.locator('#root')).toHaveAttribute('data-clarity-mask', 'true');
  await page.getByRole('button', { name: '이용 통계 허용 중' }).click();
  await Promise.all([page.waitForLoadState('load'), page.getByRole('button', { name: '거부 / 철회' }).click()]);
  await expect(page.locator('script[src*="clarity.ms/tag/"]')).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.getItem('djc-clarity-consent'))).toBe('denied');
});

test('URL 또는 유입 주소에 쿼리가 있으면 Clarity를 시작하지 않는다', async ({ page }) => {
  await setup(page, 'granted');
  await page.addInitScript(() => localStorage.setItem('djc-clarity-consent', 'granted'));
  await page.goto('/terms?keyword=FAKE_PRIVATE_SEARCH');
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  await expect(page.locator('script[src*="clarity.ms/tag/"]')).toHaveCount(0);
  await page.goto('/terms', { referer: 'https://example.com/?token=FAKE_TOKEN' });
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  await expect(page.locator('script[src*="clarity.ms/tag/"]')).toHaveCount(0);
});

test('SPA 주소 변경과 쿼리 링크 클릭 전에 Clarity를 중단한다', async ({ page }) => {
  await setup(page);
  await page.route('https://www.clarity.ms/tag/**', route => route.fulfill({ contentType: 'application/javascript', body: 'window.clarityCalls=[]; window.clarity=(...a)=>window.clarityCalls.push([location.href,...a]); window.clarity.v="test";' }));
  await page.goto('/terms');
  await page.getByRole('button', { name: '이용 통계 설정' }).click();
  await page.getByRole('button', { name: '통계 수집 허용' }).click();
  await expect.poll(() => page.evaluate(() => window.clarity?.v)).toBe('test');
  await page.evaluate(() => history.pushState({}, '', '/terms?keyword=FAKE_PRIVATE_SEARCH'));
  const calls = await page.evaluate(() => window.clarityCalls);
  expect(calls).toContainEqual([new URL('/terms', page.url()).href, 'stop']);
  expect(JSON.stringify(calls)).not.toContain('FAKE_PRIVATE_SEARCH');
});

test('Google Analytics는 동의 후 안전한 페이지 경로만 수동 기록하고 철회하면 중단한다', async ({ page }) => {
  await setup(page);
  await page.route('https://www.googletagmanager.com/gtag/js**', route => route.fulfill({ status: 200, contentType: 'text/javascript', body: '' }));
  await page.goto('/jobs?keyword=private%40example.com&token=secret');
  const initial = await page.evaluate(() => window.dataLayer.map(args => Array.from(args)));
  expect(initial[0]).toEqual(['consent', 'default', { analytics_storage: 'denied', ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied' }]);
  await page.getByRole('button', { name: '이용 통계 설정' }).click();
  await page.getByRole('button', { name: '통계 수집 허용' }).click();
  await expect(page.locator('script[src*="G-CWT9EYZSQF"]')).toHaveCount(1);
  const calls = await page.evaluate(() => window.dataLayer.map(args => Array.from(args)));
  const views = calls.filter(args => args[0] === 'event' && args[1] === 'page_view');
  expect(calls.findIndex(args => args[0] === 'consent' && args[1] === 'update' && args[2].analytics_storage === 'granted')).toBeLessThan(calls.findIndex(args => args[0] === 'config'));
  expect(calls.find(args => args[0] === 'consent' && args[1] === 'update' && args[2].analytics_storage === 'granted')[2]).toMatchObject({ ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied' });
  expect(views).toHaveLength(1);
  expect(views[0][2].page_location).toBe(new URL('/jobs', page.url()).href);
  expect(JSON.stringify(calls)).not.toMatch(/secret|private%40|private@/);
  await page.getByRole('button', { name: '이용 통계 허용 중' }).click();
  await page.getByRole('button', { name: '거부 / 철회' }).click();
  expect(await page.evaluate(() => window['ga-disable-G-CWT9EYZSQF'])).toBe(true);
  const after = await page.evaluate(() => window.dataLayer.map(args => Array.from(args)));
  expect(after.at(-1)).toEqual(['consent', 'update', { analytics_storage: 'denied', ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied' }]);
});

test('기존 자체 통계 동의만으로 Google 태그를 불러오지 않는다', async ({ page }) => {
  await setup(page, 'granted');
  await page.goto('/terms');
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  await expect(page.locator('script[src*="googletagmanager.com/gtag/js"]')).toHaveCount(0);
  await page.route('https://www.googletagmanager.com/gtag/js**', route => route.fulfill({ status: 200, contentType: 'text/javascript', body: '' }));
  await page.getByRole('button', { name: '이용 통계 허용 중' }).click();
  await page.getByRole('button', { name: '통계 수집 허용' }).click();
  await expect(page.locator('script[src*="G-CWT9EYZSQF"]')).toHaveCount(1);
});
test('동의 후 채널을 보존하고 검색어·토큰·이메일을 전송하지 않는다', async ({ page }) => {
  const events = await setup(page, 'granted');
  await page.goto('/terms?utm_source=velog&utm_medium=social&utm_campaign=launch&token=secret&keyword=private-email@example.com');
  await expect.poll(() => events.length).toBe(1);
  expect(events[0]).toMatchObject({ source: 'velog', medium: 'social', campaign: 'launch', kind: 'PAGE_VIEW', page: 'terms' });
  expect(JSON.stringify(events)).not.toMatch(/secret|private-email|keyword|token/);
});
test('StrictMode의 페이지 이벤트는 한 번만 집계한다', async ({ page }) => {
  const events = await setup(page, 'granted');
  await page.goto('/jobs?keyword=Java');
  await expect.poll(() => events.length).toBe(2);
  expect(events.map((e) => e.kind).sort()).toEqual(['PAGE_VIEW', 'SEARCH']);
});
test('추적 거부 브라우저는 동의 설정이 있어도 수집하지 않는다', async ({ page }) => {
  const events = await setup(page, 'granted');
  await page.addInitScript(() => localStorage.setItem('djc-clarity-consent', 'granted'));
  await page.addInitScript(() => Object.defineProperty(navigator, 'doNotTrack', { value: '1' }));
  await page.goto('/terms');
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  expect(events).toHaveLength(0);
  await expect(page.locator('script[src*="clarity.ms/tag/"]')).toHaveCount(0);
});
test('철회 시 이후 전송과 브라우저 식별자를 제거한다', async ({ page }) => {
  const events = await setup(page, 'granted');
  await page.goto('/terms');
  await expect.poll(() => events.length).toBe(1);
  await page.getByRole('button', { name: '이용 통계 허용 중' }).click();
  await page.getByRole('button', { name: '거부 / 철회' }).click();
  expect(await page.evaluate(() => localStorage.getItem('djc-analytics-visitor'))).toBeNull();
  expect(await page.evaluate(() => sessionStorage.getItem('djc-analytics-session'))).toBeNull();
});
test('공유는 쿼리와 인증 정보 없는 공고 URL만 복사한다', async ({ page }) => {
  const events = await setup(page, 'granted');
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'share', { value: undefined });
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: async (value) => { window.copiedUrl = value; } } });
  });
  await page.goto('/job/7?token=secret&utm_source=velog');
  await page.getByRole('button', { name: '공고 공유' }).click();
  await expect(page.getByRole('status')).toContainText('공고 링크를 복사했습니다.');
  expect(await page.evaluate(() => window.copiedUrl)).toBe(new URL('/job/7/java-%EA%B0%9C%EB%B0%9C%EC%9E%90', page.url()).href);
  await expect.poll(() => events.filter((e) => e.kind === 'SHARE').length).toBe(1);
});
test('수집 장애가 공고 탐색을 막지 않고 동일 eventId로 재시도한다', async ({ page }) => {
  await setup(page, 'granted');
  const events = [];
  await page.route('**/analytics/events', (route) => { events.push(route.request().postDataJSON()); return route.fulfill({ status: events.length === 1 ? 503 : 204 }); });
  await page.goto('/terms');
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  await expect.poll(() => events.length).toBe(2);
  expect(events[0].eventId).toBe(events[1].eventId);
});

