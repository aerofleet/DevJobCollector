import { expect, test } from '@playwright/test';

const job = { id: 5, companyName: '테스트 기업', state: 'IN_REVIEW', version: 4, content: { title: '백엔드 개발자', description: '업무 설명', jobCategory: 'backend', experience: '신입', location: '서울', hireType: '정규직', techStacks: 'Java', endDate: '2030-12-31', applyUrl: 'https://example.com/jobs' } };
async function setup(page, role = 'ADMIN') {
  const writes = [];
  let current = { ...job };
  const history = [{ id: 1, companyJobId: 5, title: job.content.title, companyName: job.companyName, action: 'APPROVE', actorType: 'ADMIN', actorId: 12, reason: '이전 승인 기록', createdAt: '2026-10-13T12:00:00' }];
  await page.route('**/api/v1/admin/**', (route) => {
    const req = route.request(), path = new URL(req.url()).pathname;
    const reply = (data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ data }) });
    if (path.endsWith('/me')) return reply({ id: 1, role, name: '관리자' });
    if (path.endsWith('/history')) {
      const params = new URL(req.url()).searchParams;
      const rows = history.filter((h) => (!params.get('action') || h.action === params.get('action')) && (!params.get('jobId') || String(h.companyJobId) === params.get('jobId')));
      return reply({ content: rows, totalPages: 1, totalElements: rows.length });
    }
    if (path.endsWith('/review')) {
      const body = req.postDataJSON(); writes.push(body);
      current = { ...current, state: { APPROVE: 'PUBLISHED', SUSPEND: 'SUSPENDED', FORCE_CLOSE: 'CLOSED', DELETE: 'DELETED' }[body.action], version: current.version + 1 };
      history.unshift({ id: history.length + 1, companyJobId: 5, title: job.content.title, companyName: job.companyName, action: body.action, actorType: 'ADMIN', actorId: 1, reason: body.reason, createdAt: '2026-10-14T12:00:00' });
      return reply(current);
    }
    if (path.endsWith('/5')) return reply({ job: current, history, duplicates: [{ id: 42, title: '기존 개발자 공고', companyName: '테스트 기업' }] });
    return reply({ content: [current], totalPages: 1 });
  });
  return writes;
}
test('검수자는 전체 내용과 중복 후보를 확인하고 사유 및 버전으로 승인한다', async ({ page }) => {
  const writes = await setup(page); await page.goto('/company-jobs');
  await page.getByRole('button', { name: '상세 검수' }).click();
  await expect(page.getByText('업무 설명', { exact: true })).toBeVisible();
  await expect(page.getByText('#42 기존 개발자 공고', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: '승인 및 게시' }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('사유');
  await page.getByLabel('처리 사유', { exact: true }).fill('기업 및 지원 링크 확인');
  await page.getByRole('button', { name: '승인 및 게시' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(writes).toEqual([{ action: 'APPROVE', expectedVersion: 4, reason: '기업 및 지원 링크 확인' }]);
});
test('REVIEWER는 검수 내용을 읽지만 승인 버튼은 없다', async ({ page }) => {
  await setup(page, 'REVIEWER'); await page.goto('/company-jobs');
  await page.getByRole('button', { name: '상세 검수' }).click();
  await expect(page.getByText('업무 설명', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '승인 및 게시' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '공고 삭제', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '공고 숨김', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '강제마감', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '이 공고 전체 이력' }).click();
  await expect(page.getByRole('cell', { name: '이전 승인 기록' })).toBeVisible();
});

for (const [action, button, tab] of [['SUSPEND', '공고 숨김', '숨김 내역'], ['FORCE_CLOSE', '강제마감', '강제마감 내역'], ['DELETE', '공고 삭제', '삭제 내역']]) {
  test(`${button}은 확인 및 사유 입력 후 이력에 남고 이전 승인 기록도 유지한다`, async ({ page }) => {
    const writes = await setup(page); await page.goto('/company-jobs');
    await page.getByRole('button', { name: '상세 검수' }).click();
    await page.getByRole('button', { name: button, exact: true }).click();
    expect(writes).toHaveLength(0);
    await page.getByRole('button', { name: '처리 확정' }).click();
    await expect(page.getByRole('dialog').getByRole('alert')).toContainText('사유');
    await page.getByLabel('처리 사유', { exact: true }).fill('기업 요청 확인');
    await page.getByRole('button', { name: '처리 확정' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(writes).toEqual([{ action, expectedVersion: 4, reason: '기업 요청 확인' }]);
    await page.getByRole('button', { name: tab, exact: true }).click();
    await expect(page.getByRole('cell', { name: '기업 요청 확인' })).toBeVisible();
    await expect(page.getByRole('cell', { name: '관리자 #1', exact: true })).toBeVisible();
    await page.getByRole('button', { name: '승인 내역', exact: true }).click();
    await expect(page.getByRole('cell', { name: '이전 승인 기록' })).toBeVisible();
    if (action === 'DELETE') {
      await page.getByRole('button', { name: '공고 상세' }).click();
      await expect(page.getByText('삭제 처리된 공고입니다.', { exact: false })).toBeVisible();
      await expect(page.getByRole('button', { name: '공고 삭제', exact: true })).toHaveCount(0);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('빈 이력 안내와 실패 후 재시도', async ({ page }) => {
  await setup(page); await page.goto('/company-jobs');
  await page.getByRole('button', { name: '삭제 내역', exact: true }).click();
  await expect(page.getByText('조건에 맞는 처리 내역이 없습니다.')).toBeVisible();
  await page.route('**/company-submissions/history?**', (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }), { times: 1 });
  await page.getByRole('button', { name: '승인 내역', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await expect(page.getByRole('cell', { name: '이전 승인 기록' })).toBeVisible();
});
