import { expect, test } from '@playwright/test';

test('직접 등록 상세는 일반 텍스트를 표시하고 조회와 지원 클릭을 구분한다', async ({ page }) => {
  const events = [];
  await page.addInitScript(() => localStorage.setItem('accessToken', 'member-token'));
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    const reply = (data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) });
    if (path === '/api/v1/members/me') return reply({ id: 42, name: '회원', role: 'USER', profileStatus: 'ACTIVE' });
    if (path.endsWith('/engagement')) { events.push(route.request().postDataJSON()); return route.fulfill({ status: 204 }); }
    if (path === '/api/v1/jobs/91') return reply({ id: 91, title: '직접 등록 테스트', sourcePlatform: 'COMPANY_DIRECT', companyName: '기업', startDate: '2026-01-01', endDate: '2030-12-31', applyQual: '<script>plain text</script>', originalUrl: 'https://example.com/jobs', techStacks: [], files: [], isActive: true });
    return reply({});
  });
  await page.goto('/job/91');
  await expect(page.getByText('기업 직접 등록', { exact: true })).toBeVisible();
  await expect(page.getByText('<script>plain text</script>', { exact: true })).toBeVisible();
  await expect.poll(() => events.filter((e) => e.kind === 'VIEW').length).toBe(1);
  await page.evaluate(() => { window.open = () => null; });
  await page.getByRole('button', { name: /지원하기/ }).click();
  await expect.poll(() => events.filter((e) => e.kind === 'APPLY_CLICK').length).toBe(1);
  expect(events[0].eventId).not.toEqual(events[1].eventId);
});
