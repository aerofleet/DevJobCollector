import { expect, test } from '@playwright/test';

test('관리자는 Stripe 급여 근거를 쓰기 없이 확인한다', async ({ page }) => {
  const writes = [];
  const job = { id: 11051, title: 'Account Executive', companyName: 'Stripe', sourcePlatform: 'GREENHOUSE',
    version: 3, moderationStatus: 'ACTIVE', active: true, endDate: '9999-12-31', originalUrl: 'https://stripe.com/jobs/search?gh_jid=8330801' };
  await page.route('**/api/v1/admin/**', route => {
    const request = route.request();
    if (request.method() !== 'GET') writes.push(request.method());
    const path = new URL(request.url()).pathname;
    const reply = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/me')) return reply({ data: { id: 1, name: '관리자', role: 'ADMIN' } });
    if (path.endsWith('/stripe-salary/preview')) return reply({ status: 'PRESENT', previousText: null, sourceHash: 'a'.repeat(64),
      sourceUrl: job.originalUrl, paragraphs: ['Annual OTE A$306,500 - A$459,700 for the primary location.'] });
    if (path.endsWith('/jobs/11051')) return reply({ data: job });
    return reply({ data: { content: [job], totalElements: 1, totalPages: 1 } });
  });
  await page.goto('/jobs');
  await page.getByRole('button', { name: '상세', exact: true }).click();
  await page.getByRole('button', { name: '급여 원문 확인' }).click();
  await expect(page.getByText('기존 정보: 없음')).toBeVisible();
  await expect(page.getByText(/Annual OTE A\$306,500/)).toBeVisible();
  await expect(page.getByRole('link', { name: 'Stripe 원문 열기' })).toHaveAttribute('href', job.originalUrl);
  expect(writes).toEqual([]);
});
