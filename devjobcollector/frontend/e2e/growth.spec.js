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
  await page.addInitScript(() => Object.defineProperty(navigator, 'doNotTrack', { value: '1' }));
  await page.goto('/terms');
  await expect(page.getByRole('heading', { name: '데브잡스 이용약관', exact: true })).toBeVisible();
  expect(events).toHaveLength(0);
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
  expect(await page.evaluate(() => window.copiedUrl)).toBe('http://127.0.0.1:4174/job/7');
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
