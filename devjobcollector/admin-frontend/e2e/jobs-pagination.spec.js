import { expect, test } from '@playwright/test';

test('공고 페이지 번호와 크기를 변경해도 검색 조건을 유지한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', route => route.fulfill({ json: { data: { id: 1, name: '관리자', role: 'ADMIN' } } }));
  await page.route('**/api/v1/admin/jobs?*', route => {
    const params = new URL(route.request().url()).searchParams;
    const size = Number(params.get('size')), current = Number(params.get('page'));
    const filtered = params.get('keyword') === '개발자' && params.get('status') === 'HIDDEN';
    const total = filtered ? 137 : 200;
    return route.fulfill({ json: { data: { content: [{ id: current + 1,
      title: `${filtered ? '검색 결과' : '공고'} ${current * size + 1}`, companyName: '기업', moderationStatus: 'ACTIVE' }],
    page: { number: current, size, totalElements: total, totalPages: Math.ceil(total / size) } } } });
  });
  await page.goto('/jobs');
  await page.getByLabel('공고 또는 기업명').fill('개발자');
  await page.getByRole('button', { name: '검색' }).click();
  await page.getByLabel('관리 상태').selectOption('HIDDEN');
  const nav = page.getByRole('navigation', { name: '공고 목록 페이지' });
  await expect(nav).toContainText('전체 137건');
  await expect(nav).toContainText('1 / 7 페이지');
  await nav.getByRole('button', { name: '3페이지' }).click();
  await expect(page.getByRole('cell', { name: '검색 결과 41', exact: true })).toBeVisible();
  await page.reload();
  await expect(nav).toContainText('3 / 7 페이지');
  await expect(page.getByRole('cell', { name: '검색 결과 41', exact: true })).toBeVisible();
  await expect(page.getByLabel('공고 또는 기업명')).toHaveValue('개발자');
  await expect(page.getByLabel('관리 상태')).toHaveValue('HIDDEN');
  await expect(nav.getByLabel('페이지 이동')).toHaveValue('3');
  await nav.getByLabel('페이지 이동').fill('7');
  await nav.getByRole('button', { name: '이동', exact: true }).click();
  await expect(page.getByRole('cell', { name: '검색 결과 121', exact: true })).toBeVisible();
  await expect(nav.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
  await nav.getByLabel('페이지 이동').fill('8');
  await nav.getByRole('button', { name: '이동', exact: true }).click();
  await expect(nav.getByRole('alert')).toContainText('1부터 7까지');
  await nav.getByLabel('페이지당').selectOption('50');
  await expect(nav).toContainText('1 / 3 페이지');
  await expect(page.getByRole('cell', { name: '검색 결과 1', exact: true })).toBeVisible();
  await nav.getByRole('button', { name: '3페이지' }).click();
  await expect(page.getByRole('cell', { name: '검색 결과 101', exact: true })).toBeVisible();
  await page.reload();
  await expect(nav).toContainText('3 / 3 페이지');
  await expect(nav.getByLabel('페이지당')).toHaveValue('50');
  await expect(page.getByRole('cell', { name: '검색 결과 101', exact: true })).toBeVisible();
});
