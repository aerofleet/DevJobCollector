import { expect, test } from '@playwright/test';

const job = { id: 5, companyName: '테스트 기업', state: 'IN_REVIEW', version: 4, content: { title: '백엔드 개발자', description: '업무 설명', jobCategory: 'backend', experience: '신입', location: '서울', hireType: '정규직', techStacks: 'Java', endDate: '2030-12-31', applyUrl: 'https://example.com/jobs' } };
async function setup(page, role = 'ADMIN') {
  const writes = [];
  await page.route('**/api/v1/admin/**', (route) => {
    const req = route.request(), path = new URL(req.url()).pathname;
    const reply = (data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ data }) });
    if (path.endsWith('/me')) return reply({ id: 1, role, name: '관리자' });
    if (path.endsWith('/review')) { writes.push(req.postDataJSON()); return reply({ ...job, state: 'PUBLISHED' }); }
    if (path.endsWith('/5')) return reply({ job, history: [], duplicates: [{ id: 42, title: '기존 개발자 공고', companyName: '테스트 기업' }] });
    return reply({ content: [job], totalPages: 1 });
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
});
