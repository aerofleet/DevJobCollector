import { expect, test } from '@playwright/test';

async function setup(page, role = 'ADMIN', conflict = false) {
  const writes = [];
  let companyJob = { id: 5, publishedJobId: 90, version: 7, state: 'PUBLISHED', liveStatus: 'ACTIVE' };
  const publicJob = () => ({ id: 90, title: '승인된 기업 공고', companyName: '기업', sourcePlatform: 'COMPANY_DIRECT', version: 2, moderationStatus: companyJob.liveStatus, active: companyJob.liveStatus === 'ACTIVE', endDate: '2030-12-31', originalUrl: 'https://example.com' });
  await page.route('**/api/v1/admin/**', (route) => {
    const req = route.request(), path = new URL(req.url()).pathname;
    const reply = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ data }) });
    if (path.endsWith('/me')) return reply({ id: 1, name: '관리자', role });
    if (path === '/api/v1/admin/jobs/90') return reply(publicJob());
    if (path.endsWith('/company-submissions/published/90')) return reply(companyJob);
    if (req.method() === 'POST') {
      writes.push({ path, body: req.postDataJSON() });
      if (conflict) return reply({}, 409);
      const action = req.postDataJSON().action;
      companyJob = { ...companyJob, version: companyJob.version + 1, state: { SUSPEND: 'SUSPENDED', FORCE_CLOSE: 'CLOSED', DELETE: 'DELETED' }[action], liveStatus: action === 'FORCE_CLOSE' ? 'CLOSED' : 'HIDDEN' };
      return reply(companyJob);
    }
    return reply({ content: [publicJob()], totalElements: 1, totalPages: 1 });
  });
  await page.goto('/jobs');
  await page.getByRole('button', { name: '상세', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  return writes;
}

for (const [button, action, status] of [['공고 숨김', 'SUSPEND', '숨김'], ['강제 마감', 'FORCE_CLOSE', '강제 마감'], ['공고 삭제', 'DELETE', '삭제됨']]) {
  test(`승인된 직접 공고 상세에서 ${button} 처리`, async ({ page }) => {
    const writes = await setup(page);
    for (const name of ['공고 숨김', '강제 마감', '공고 삭제']) await expect(page.getByRole('button', { name, exact: true })).toBeEnabled();
    await page.getByRole('button', { name: button, exact: true }).click();
    await page.getByRole('button', { name: '변경 확정' }).click();
    await expect(page.getByRole('dialog').getByRole('alert')).toContainText('사유');
    expect(writes).toHaveLength(0);
    await page.getByLabel('변경 사유').fill('관리자 확인');
    await page.getByRole('button', { name: '변경 확정' }).click();
    await expect(page.getByRole('dialog').locator('dd').filter({ hasText: new RegExp(`^${status}$`) })).toBeVisible();
    expect(writes).toEqual([{ path: '/api/v1/admin/jobs/company-submissions/5/review', body: { action, expectedVersion: 7, reason: '관리자 확인' } }]);
    await expect(page.getByRole('button', { name: '재활성', exact: true })).toHaveCount(0);
    if (action === 'DELETE') for (const name of ['공고 숨김', '강제 마감', '공고 삭제']) await expect(page.getByRole('button', { name, exact: true })).toHaveCount(0);
  });
}

test('조회 전용 관리자는 승인된 기업 공고도 처리할 수 없다', async ({ page }) => {
  await setup(page, 'REVIEWER');
  for (const name of ['공고 숨김', '강제 마감', '공고 삭제']) await expect(page.getByRole('button', { name, exact: true })).toHaveCount(0);
});

test('검수 공고 버전 충돌은 실패로 표시하고 화면 상태를 유지한다', async ({ page }) => {
  await setup(page, 'ADMIN', true);
  await page.getByRole('button', { name: '공고 삭제', exact: true }).click();
  await page.getByLabel('변경 사유').fill('확인');
  await page.getByRole('button', { name: '변경 확정' }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('상태가 변경');
  await expect(page.getByRole('dialog').getByText('노출', { exact: true })).toBeVisible();
});
