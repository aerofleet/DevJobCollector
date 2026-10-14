import { test, expect } from '@playwright/test';
test('유입 통계는 활성 세션율을 계산하고 미성숙 D7을 N/A로 표시', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => route.fulfill({ json: { data: { id: 1, name: '관리자', role: 'SUPER_ADMIN' } } }));
  await page.route('**/api/v1/admin/dashboard/summary', (route) => route.fulfill({ json: { data: { metrics: {}, signupTrend: [], reviewQueue: {} } } }));
  await page.route('**/api/v1/admin/dashboard/growth', (route) => route.fulfill({ json: {
    scope: '동의한 브라우저 기준', stages: [{ kind: 'PAGE_VIEW', events: 12, visitors: 8, sessions: 10 }],
    channels: [{ source: 'velog', medium: 'social', campaign: 'launch', sessions: 10, activatedSessions: 3 }],
    retention: { eligibleVisitors: 0, returnedVisitors: 0 },
  } }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '최근 28일 유입과 활성화' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '30.0%' })).toBeVisible();
  await expect(page.getByText('N/A · 7일차 관찰이 끝난 방문자가 아직 없습니다.')).toBeVisible();
});
